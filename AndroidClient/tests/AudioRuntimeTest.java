package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AudioRuntimeTest {
    static final class Fake implements AudioRuntime.Device {
        long now = 1_000_000_000, started, floats;
        long openDelay, readDelay;
        int underruns;
        int capacityFrames, reportedBufferFrames = 480;
        int opens, resets, closes, offers, writes, shortWrite = 137;
        boolean active, routeChange, noTimestamp, noPCM, wrongPTS, primeMarkers;
        final ArrayList<Float> rendered = new ArrayList<>();
        final ArrayDeque<AudioRuntime.PCM> output = new ArrayDeque<>();
        public void open(byte[] cookie) { now += openDelay; active = true; started = now; floats = 0; output.clear(); opens++; }
        public void resetDecoder() { output.clear(); resets++; }
        public boolean offer(byte[] packet, long pts) {
            offers++;
            ByteBuffer pcm = ByteBuffer.allocate(480 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < 480; i++) {
                boolean prime = primeMarkers && offers == 1 && i < 240;
                pcm.putShort((short) (prime ? 30000 : 8192)); pcm.putShort((short) (prime ? -30000 : -16384));
            }
            pcm.flip();
            if (!noPCM) output.add(new AudioRuntime.PCM(wrongPTS ? pts + 1_000_000 : pts,
                    pcm, AudioDecodeMapping.Encoding.S16_LE));
            return true;
        }
        public AudioRuntime.PCM poll() { return output.poll(); }
        public AudioRuntime.Stamp timestamp() {
            now += readDelay;
            if (noTimestamp) return null;
            long requested = (now - started) * 48000 / 1_000_000_000L;
            if (floats > 0 && requested > floats / 2) underruns++;
            long frame = Math.min(floats / 2, requested);
            return new AudioRuntime.Stamp(frame, started + frame * 1_000_000_000L / 48000);
        }
        public int write(float[] pcm, int offset, int count) {
            writes++; int n = Math.min(count, shortWrite);
            if (capacityFrames > 0 && n > 0) {
                long consumed = Math.min(floats / 2, (now - started) * 48000 / 1_000_000_000L);
                n = (int) Math.min(n, Math.max(0, capacityFrames * 2L - (floats - consumed * 2)));
            }
            if (n > 0) { floats += n; for (int i = 0; i < n; i++) rendered.add(pcm[offset + i]); }
            return n;
        }
        public int bufferFrames() { return reportedBufferFrames; }
        public boolean routeChanged() { boolean changed = routeChange; routeChange = false; return changed; }
        public void close() { if (active) closes++; active = false; output.clear(); }
    }
    static AudioPackets.Batch packet(long sequence, boolean start) {
        return new AudioPackets.Batch(1, sequence, start, List.of(new byte[]{1}));
    }
    static void pump(AudioRuntime runtime, Fake fake, int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) { runtime.tick(fake.now); fake.now += 5_000_000; }
    }
    static int nonzeroFrames(Fake fake) {
        int count = 0;
        for (int i = 0; i + 1 < fake.rendered.size(); i += 2) if (fake.rendered.get(i) != 0) count++;
        return count;
    }
    static void sustainedIdle(long interval, int writeLimit) throws Exception {
        var fake = new Fake(); fake.capacityFrames = fake.reportedBufferFrames = 1920;
        fake.shortWrite = writeLimit;
        var runtime = new AudioRuntime(fake, () -> fake.now);
        runtime.format(1, 480, 0, new byte[]{1});
        runtime.packet(packet(0, true), 1000, fake.now, fake.now);
        pump(runtime, fake, 100); // acquisition and final editable tail have completed.
        assert nonzeroFrames(fake) == 480;
        for (int i = 0; i < 100; i++) {
            int beforeCalls = fake.writes;
            runtime.tick(fake.now);
            assert fake.writes - beforeCalls <= 8;
            fake.now += interval;
        }
        assert fake.underruns == 0 : "idle underflow at " + interval + ": " + fake.underruns;
        assert runtime.diagnostics().contains("clock=READY");
        runtime.enabled(false);
        long pausedWrites = fake.floats;
        fake.now += 2_000_000_000L; runtime.tick(fake.now);
        assert !fake.active && fake.floats == pausedWrites;
        runtime.enabled(true);
        runtime.packet(packet(1, true), 999 + fake.now / 1e9, fake.now, fake.now);
        pump(runtime, fake, 100);
        for (int i = 0; i < 100; i++) { runtime.tick(fake.now); fake.now += interval; }
        assert fake.opens == 2 && fake.underruns == 0;
        assert runtime.diagnostics().contains("clock=READY");
        // Packets arriving during reacquisition can legitimately be late. A fresh packet on
        // the acquired clock must render every frame including its final editable tail.
        int beforeFreshPacket = nonzeroFrames(fake);
        runtime.packet(packet(2, false), 999 + fake.now / 1e9, fake.now, fake.now);
        pump(runtime, fake, 60);
        assert nonzeroFrames(fake) - beforeFreshPacket == 480;
        // A new undecoded packet must prevent gap silence from overtaking its BODY or TAIL.
        fake.noPCM = true;
        runtime.packet(packet(3, true), 999 + fake.now / 1e9, fake.now, fake.now);
        long heldWrites = fake.floats;
        boolean stopped = false;
        for (int i = 0; i < 80; i++) {
            try { runtime.tick(fake.now); } catch (IllegalStateException expected) { stopped = true; break; }
            assert fake.floats == heldWrites;
            fake.now += 15_000_000;
        }
        assert stopped && fake.floats == heldWrites;
        runtime.close();
    }
    public static void main(String[] args) throws Exception {
        sustainedIdle(15_000_000, Integer.MAX_VALUE);
        sustainedIdle(30_000_000, 401); // odd short writes, finite 40 ms device queue.
        System.out.println("PASS sustained READY idle: 15/30ms cadence, finite queue, long/401-float short writes, <=8 calls/tick, mute/resume, both tails, no fill over unresolved PCM");
        assert AudioRuntime.streamFPS(120, 60) == 120;
        assert AudioRuntime.streamFPS(29.97, 120) == 29.97;
        assert AudioRuntime.streamFPS(0, 60) == 60;
        assert AudioRuntime.streamFPS(-1, 120) == 120;
        assert AudioRuntime.streamFPS(Double.NaN, 120) == 120;
        assert AudioRuntime.streamFPS(Double.POSITIVE_INFINITY, 0) == 60;
        System.out.println("PASS reported positive stream.fps takes priority; absent/nonpositive/nonfinite falls back to valid cap or 60");
        var delayed = new Fake(); delayed.openDelay = 1_500_000_000L; delayed.readDelay = 2_000_000L;
        delayed.shortWrite = Integer.MAX_VALUE;
        var delayedRuntime = new AudioRuntime(delayed, () -> delayed.now);
        delayedRuntime.format(1, 480, 0, new byte[]{1});
        delayedRuntime.packet(packet(0, true), 1000, delayed.now, delayed.now);
        pump(delayedRuntime, delayed, 20);
        assert delayedRuntime.diagnostics().contains("clock=READY") : delayedRuntime.diagnostics();
        delayedRuntime.close();
        var blockedRead = new Fake(); blockedRead.noTimestamp = true; blockedRead.readDelay = 1_100_000_000L;
        var blockedRuntime = new AudioRuntime(blockedRead, () -> blockedRead.now);
        blockedRuntime.format(1, 480, 0, new byte[]{1});
        blockedRuntime.packet(packet(0, true), 1000, blockedRead.now, blockedRead.now);
        boolean readTimedOut = false;
        try { blockedRuntime.tick(blockedRead.now); } catch (IllegalStateException expected) { readTimedOut = true; }
        assert readTimedOut; blockedRuntime.close();
        var slow = new Fake(); slow.shortWrite = Integer.MAX_VALUE;
        var slowRuntime = new AudioRuntime(slow, () -> slow.now);
        slowRuntime.format(1, 480, 0, new byte[]{1});
        slowRuntime.packet(packet(0, true), 1000, slow.now, slow.now);
        for (int i = 0; i < 10; i++) { slowRuntime.tick(slow.now); slow.now += 15_000_000; }
        assert slow.underruns == 0 : slow.underruns;
        assert slowRuntime.diagnostics().contains("clock=READY") : slowRuntime.diagnostics();
        slowRuntime.close();
        System.out.println("PASS runtime: 1.5s open excluded from acquisition deadline, clock sampled after 2ms timestamp call, 15ms timer replenishes silence without underrun");
        var fake = new Fake(); var runtime = new AudioRuntime(fake, () -> fake.now);
        fake.primeMarkers = true;
        runtime.format(1, 480, 240, new byte[]{1});
        for (int i = 0; i < 100; i++) {
            runtime.frame(1000 + i * .01, fake.now);
            runtime.packet(packet(i, i == 0), 1000 + i * .01, fake.now, fake.now);
            pump(runtime, fake, 2);
        }
        assert fake.opens == 1 && fake.offers == 100 && fake.floats > 24000 * 2;
        runtime.format(1, 480, 240, new byte[]{1});
        assert fake.active && fake.opens == 1; // A repeated identical format must not reset playback.
        pump(runtime, fake, 200); // final deadline tail and ongoing hardware clock during silence.
        int nonzeroFrames = 0;
        for (int i = 0; i + 1 < fake.rendered.size(); i += 2) {
            float left = fake.rendered.get(i), right = fake.rendered.get(i + 1);
            assert left >= 0 && left <= .25f && right >= -.5f && right <= 0; // priming markers never escape.
            assert Math.abs(right + 2 * left) < 1e-6f; // asymmetric channels survive every odd write/fade.
            if (left != 0) nonzeroFrames++;
        }
        assert nonzeroFrames == 100 * 480 - 240 : nonzeroFrames; // priming removed once, no lost tail.
        runtime.enabled(false); assert !fake.active;
        int offers = fake.offers;
        for (int i = 100; i < 110; i++) {
            runtime.packet(packet(i, false), 1000 + i * .01, fake.now, fake.now); pump(runtime, fake, 2);
        }
        assert fake.offers == offers;
        runtime.enabled(true);
        runtime.packet(packet(110, false), 1001.10, fake.now, fake.now); pump(runtime, fake, 2);
        assert fake.opens == 2;
        fake.routeChange = true; pump(runtime, fake, 1); assert !fake.active;
        runtime.packet(packet(111, false), 1001.11, fake.now, fake.now); pump(runtime, fake, 2);
        assert fake.opens == 3;
        fake.now += 11_000_000_000L; runtime.tick(fake.now); assert !fake.active;
        runtime.close();
        System.out.println("PASS fake device: 100 asymmetric nonzero PCM packets, exactly 47760 nonzero frames (240 priming removed once), 137-float partial writes, deadline tail, mute/unmute, route rebuild, 10s idle close");

        Fake badClock = new Fake(); badClock.noTimestamp = true;
        AudioRuntime clockRuntime = new AudioRuntime(badClock, () -> badClock.now);
        clockRuntime.format(1, 480, 0, new byte[]{1});
        clockRuntime.packet(packet(0, true), 1000, badClock.now, badClock.now);
        boolean failed = false;
        try { pump(clockRuntime, badClock, 201); } catch (IllegalStateException expected) { failed = true; }
        assert failed && badClock.floats <= 12000 * 2; clockRuntime.close();
        Fake badPts = new Fake(); badPts.wrongPTS = true;
        AudioRuntime ptsRuntime = new AudioRuntime(badPts, () -> badPts.now);
        ptsRuntime.format(1, 480, 0, new byte[]{1});
        ptsRuntime.packet(packet(0, true), 1000, badPts.now, badPts.now);
        failed = false;
        try { pump(ptsRuntime, badPts, 5); } catch (IllegalStateException expected) { failed = expected.getMessage().contains("PTS"); }
        assert failed; ptsRuntime.close();
        System.out.println("PASS fake device: absent presentation timestamp bounded silent startup, unknown PCM PTS explicitly rejected");
        Fake stuck = new Fake(); stuck.noPCM = true;
        var stuckRuntime = new AudioRuntime(stuck, () -> stuck.now);
        stuckRuntime.format(1, 480, 0, new byte[]{1});
        stuckRuntime.packet(packet(0, true), 1000, stuck.now, stuck.now);
        failed = false;
        try { pump(stuckRuntime, stuck, 150); } catch (IllegalStateException expected) {
            failed = expected.getMessage().contains("timeout") || expected.getMessage().contains("timestamp");
        }
        assert failed; stuckRuntime.close();
        Fake zero = new Fake(); zero.shortWrite = 0;
        var zeroRuntime = new AudioRuntime(zero, () -> zero.now);
        zeroRuntime.format(1, 480, 0, new byte[]{1});
        zeroRuntime.packet(packet(0, true), 1000, zero.now, zero.now);
        failed = false;
        try { pump(zeroRuntime, zero, 201); } catch (IllegalStateException expected) { failed = true; }
        assert failed && zero.writes <= 201 && zero.floats == 0; zeroRuntime.close();
        Fake dead = new Fake(); dead.shortWrite = -6;
        var deadRuntime = new AudioRuntime(dead, () -> dead.now);
        deadRuntime.format(1, 480, 0, new byte[]{1});
        deadRuntime.packet(packet(0, true), 1000, dead.now, dead.now);
        failed = false;
        try { pump(deadRuntime, dead, 1); } catch (IllegalStateException expected) { failed = expected.getMessage().contains("write failed"); }
        assert failed && dead.writes == 1; deadRuntime.close();
        System.out.println("PASS fake device: stalled decoder bounded failure, zero write yields (<=1 call/tick), DEAD_OBJECT rejects without retry spin");
    }
}
