package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class AudioRuntimeTest {
    static final class Fake implements AudioRuntime.Device {
        long now = 1_000_000_000, started, floats;
        int opens, resets, closes, offers, writes, shortWrite = 137;
        boolean active, routeChange, noTimestamp, noPCM, wrongPTS, primeMarkers;
        final ArrayList<Float> rendered = new ArrayList<>();
        final ArrayDeque<AudioRuntime.PCM> output = new ArrayDeque<>();
        public void open(byte[] cookie) { active = true; started = now; floats = 0; output.clear(); opens++; }
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
            if (noTimestamp) return null;
            long frame = Math.min(floats / 2, (now - started) * 48000 / 1_000_000_000L);
            return new AudioRuntime.Stamp(frame, started + frame * 1_000_000_000L / 48000);
        }
        public int write(float[] pcm, int offset, int count) {
            writes++; int n = Math.min(count, shortWrite);
            if (n > 0) { floats += n; for (int i = 0; i < n; i++) rendered.add(pcm[offset + i]); }
            return n;
        }
        public int bufferFrames() { return 480; }
        public boolean routeChanged() { boolean changed = routeChange; routeChange = false; return changed; }
        public void close() { if (active) closes++; active = false; output.clear(); }
    }
    static AudioPackets.Batch packet(long sequence, boolean start) {
        return new AudioPackets.Batch(1, sequence, start, List.of(new byte[]{1}));
    }
    static void pump(AudioRuntime runtime, Fake fake, int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) { runtime.tick(fake.now); fake.now += 5_000_000; }
    }
    public static void main(String[] args) throws Exception {
        assert AudioRuntime.streamFPS(120, 60) == 120;
        assert AudioRuntime.streamFPS(29.97, 120) == 29.97;
        assert AudioRuntime.streamFPS(0, 60) == 60;
        assert AudioRuntime.streamFPS(-1, 120) == 120;
        assert AudioRuntime.streamFPS(Double.NaN, 120) == 120;
        assert AudioRuntime.streamFPS(Double.POSITIVE_INFINITY, 0) == 60;
        System.out.println("PASS reported positive stream.fps takes priority; absent/nonpositive/nonfinite falls back to valid cap or 60");
        var fake = new Fake(); var runtime = new AudioRuntime(fake);
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
        AudioRuntime clockRuntime = new AudioRuntime(badClock);
        clockRuntime.format(1, 480, 0, new byte[]{1});
        clockRuntime.packet(packet(0, true), 1000, badClock.now, badClock.now);
        boolean failed = false;
        try { pump(clockRuntime, badClock, 201); } catch (IllegalStateException expected) { failed = true; }
        assert failed && badClock.floats <= 12000 * 2; clockRuntime.close();
        Fake badPts = new Fake(); badPts.wrongPTS = true;
        AudioRuntime ptsRuntime = new AudioRuntime(badPts);
        ptsRuntime.format(1, 480, 0, new byte[]{1});
        ptsRuntime.packet(packet(0, true), 1000, badPts.now, badPts.now);
        failed = false;
        try { pump(ptsRuntime, badPts, 5); } catch (IllegalStateException expected) { failed = expected.getMessage().contains("PTS"); }
        assert failed; ptsRuntime.close();
        System.out.println("PASS fake device: absent presentation timestamp bounded silent startup, unknown PCM PTS explicitly rejected");
        Fake stuck = new Fake(); stuck.noPCM = true;
        var stuckRuntime = new AudioRuntime(stuck);
        stuckRuntime.format(1, 480, 0, new byte[]{1});
        stuckRuntime.packet(packet(0, true), 1000, stuck.now, stuck.now);
        failed = false;
        try { pump(stuckRuntime, stuck, 150); } catch (IllegalStateException expected) {
            failed = expected.getMessage().contains("timeout") || expected.getMessage().contains("timestamp");
        }
        assert failed; stuckRuntime.close();
        Fake zero = new Fake(); zero.shortWrite = 0;
        var zeroRuntime = new AudioRuntime(zero);
        zeroRuntime.format(1, 480, 0, new byte[]{1});
        zeroRuntime.packet(packet(0, true), 1000, zero.now, zero.now);
        failed = false;
        try { pump(zeroRuntime, zero, 201); } catch (IllegalStateException expected) { failed = true; }
        assert failed && zero.writes <= 201 && zero.floats == 0; zeroRuntime.close();
        Fake dead = new Fake(); dead.shortWrite = -6;
        var deadRuntime = new AudioRuntime(dead);
        deadRuntime.format(1, 480, 0, new byte[]{1});
        deadRuntime.packet(packet(0, true), 1000, dead.now, dead.now);
        failed = false;
        try { pump(deadRuntime, dead, 1); } catch (IllegalStateException expected) { failed = expected.getMessage().contains("write failed"); }
        assert failed && dead.writes == 1; deadRuntime.close();
        System.out.println("PASS fake device: stalled decoder bounded failure, zero write yields (<=1 call/tick), DEAD_OBJECT rejects without retry spin");
    }
}
