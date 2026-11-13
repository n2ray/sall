package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Actual production AudioRuntime against a finite device with delayed presentation timestamps. */
public final class AudioDelayedRouteTest {
    static final int BUFFER = 7688, RATE = 48000;
    static final long PERIOD = 40_000_000L, DELAY = 160_000_000L;
    static final class Route implements AudioRuntime.Device {
        record Point(long frame, long at) {}
        long now = 1_000_000_000L, start, nextConsume, writtenFloats, head, firstUnderrun = -1;
        long firstAudioFrame = -1;
        int packetFrames = 480, shortWrite = Integer.MAX_VALUE;
        boolean changed, noTimestamp, frozen;
        AudioRuntime.Stamp lastStamp;
        int underruns;
        boolean starving;
        final ArrayList<Point> history = new ArrayList<>();
        final ArrayDeque<AudioRuntime.PCM> decoded = new ArrayDeque<>();
        public void open(byte[] cookie) {
            start = now; nextConsume = now + PERIOD; writtenFloats = head = 0;
            underruns = 0; firstUnderrun = -1; starving = false;
            lastStamp = null; frozen = false;
            firstAudioFrame = -1;
            history.clear(); decoded.clear(); history.add(new Point(0, start));
        }
        void advance() {
            while (nextConsume <= now) {
                long take = Math.min(1920, writtenFloats / 2 - head);
                if (take < 1920) {
                    if (!starving) { underruns++; if (firstUnderrun < 0) firstUnderrun = nextConsume; }
                    starving = true;
                } else starving = false;
                head += take; history.add(new Point(head, nextConsume)); nextConsume += PERIOD;
            }
        }
        public void resetDecoder() { decoded.clear(); }
        public boolean offer(byte[] data, long pts) {
            ByteBuffer pcm = ByteBuffer.allocate(packetFrames * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < packetFrames; i++) { pcm.putShort((short) 8192); pcm.putShort((short) -16384); }
            pcm.flip();
            decoded.add(new AudioRuntime.PCM(pts, pcm, AudioDecodeMapping.Encoding.S16_LE));
            return true;
        }
        public AudioRuntime.PCM poll() { return decoded.poll(); }
        public AudioRuntime.Stamp timestamp() {
            advance();
            if (noTimestamp) return null;
            if (frozen) return lastStamp;
            Point last = history.get(0);
            for (Point point : history) if (point.at + DELAY <= now) last = point;
            // Initial frame-zero observations are not a settled presentation clock.
            long at = last.frame == 0 ? now : last.at + DELAY;
            lastStamp = new AudioRuntime.Stamp(last.frame, at);
            return lastStamp;
        }
        public int write(float[] pcm, int offset, int count) {
            advance();
            int n = (int) Math.min(count, Math.max(0, BUFFER * 2L - (writtenFloats - head * 2)));
            n = Math.min(n, shortWrite);
            for (int i = 0; i < n && firstAudioFrame < 0; i++)
                if (pcm[offset + i] != 0) firstAudioFrame = (writtenFloats + i) / 2;
            writtenFloats += n; return n;
        }
        public int bufferFrames() { return BUFFER; }
        public int underruns() { advance(); return underruns; }
        public boolean routeChanged() { boolean value = changed; changed = false; return value; }
        public void close() { }
    }
    static void run(int frames, int shortWrite, boolean changeTiming) throws Exception {
        var route = new Route(); route.packetFrames = frames; route.shortWrite = shortWrite;
        var runtime = new AudioRuntime(route, () -> route.now);
        runtime.format(1, frames, 0, new byte[]{1});
        long sourceStart = route.now, sequence = 0;
        boolean ready = false;
        double frozenLead = 0;
        for (int i = 0; i < 400; i++, route.now += 5_000_000) {
            while (route.now - sourceStart >= sequence * (long) frames * 1_000_000_000L / RATE) {
                long arrival = sourceStart + sequence * (long) frames * 1_000_000_000L / RATE;
                runtime.packet(new AudioPackets.Batch(1, sequence, sequence == 0, List.of(new byte[]{1})),
                        1000 + sequence * (double) frames / RATE, arrival, route.now);
                sequence++;
            }
            runtime.tick(route.now);
            String d = runtime.diagnostics();
            assert number(d, "startupAllocated") <= number(d, "startupBudget");
            assert number(d, "startupBudget") <= 48000;
            if (d.contains("clock=READY")) {
                if (!ready) { ready = true; frozenLead = number(d, "writeLeadFrames"); assert frozenLead > BUFFER; }
                if (changeTiming) runtime.timing(1.0 / 120, 120);
                assert number(runtime.diagnostics(), "writeLeadFrames") == frozenLead;
            }
        }
        assert ready && route.underruns == 0 && runtime.diagnostics().contains("clock=READY");
        assert route.firstAudioFrame >= number(runtime.diagnostics(), "startupAllocated");
        for (int i = 0; i < 200; i++, route.now += 5_000_000) runtime.tick(route.now);
        assert route.underruns == 0 && runtime.diagnostics().contains("clock=READY");
        System.out.println("PASS real AudioRuntime delayed route frames=" + frames + " shortWrite=" + shortWrite
                + " firstAudioFrame=" + route.firstAudioFrame + " " + runtime.diagnostics());
        route.changed = true; runtime.tick(route.now);
        assert runtime.diagnostics() == null;
        double resetWall = 1000 + (route.now - sourceStart) / 1e9;
        runtime.packet(new AudioPackets.Batch(1, sequence++, true, List.of(new byte[]{1})), resetWall, route.now, route.now);
        assert runtime.diagnostics().contains("handoff=false");
        assert number(runtime.diagnostics(), "writeLeadFrames") == BUFFER;
        assert number(runtime.diagnostics(), "startupAllocated") == 0;
        long resetStart = route.now, localSequence = 1;
        for (int i = 0; i < 240; i++, route.now += 5_000_000) {
            while (route.now - resetStart >= localSequence * (long) frames * 1_000_000_000L / RATE) {
                long arrival = resetStart + localSequence * (long) frames * 1_000_000_000L / RATE;
                runtime.packet(new AudioPackets.Batch(1, sequence++, false, List.of(new byte[]{1})),
                        resetWall + localSequence * (double) frames / RATE, arrival, route.now);
                localSequence++;
            }
            runtime.tick(route.now);
        }
        assert route.underruns == 0 && runtime.diagnostics().contains("clock=READY");
        route.frozen = true;
        long freezeStart = route.now; boolean rejectedFrozen = false;
        for (int i = 0; i < 60; i++, route.now += 5_000_000) {
            while (route.now - resetStart >= localSequence * (long) frames * 1_000_000_000L / RATE) {
                long arrival = resetStart + localSequence * (long) frames * 1_000_000_000L / RATE;
                runtime.packet(new AudioPackets.Batch(1, sequence++, false, List.of(new byte[]{1})),
                        resetWall + localSequence * (double) frames / RATE, arrival, route.now);
                localSequence++;
            }
            try { runtime.tick(route.now); }
            catch (IllegalStateException expected) { rejectedFrozen = expected.getMessage().contains("timestamp"); break; }
        }
        assert rejectedFrozen && route.now - freezeStart <= 255_000_000L && route.underruns == 0;
        System.out.println("PASS route rebuilt with fresh budget/guard and reacquired; READY frozen timestamp rejected within 255ms");
        runtime.close();
    }
    static double number(String diagnostic, String name) {
        return Double.parseDouble(diagnostic.split(name + "=")[1].split(" ")[0]);
    }
    static void failures() throws Exception {
        var route = new Route(); route.noTimestamp = true;
        var runtime = new AudioRuntime(route, () -> route.now);
        runtime.format(1, 480, 0, new byte[]{1});
        runtime.packet(new AudioPackets.Batch(1, 0, true, List.of(new byte[]{1})), 1000, route.now, route.now);
        long initial = route.now; boolean failed = false;
        for (int i = 0; i < 220; i++, route.now += 5_000_000) {
            try { runtime.tick(route.now); }
            catch (IllegalStateException expected) { failed = true; break; }
        }
        assert failed && route.now - initial <= 1_000_000_000L && route.writtenFloats <= 48000 * 2;
        assert !runtime.diagnostics().contains("handoff=true"); runtime.close();
        var supply = new AudioStartupSupply(BUFFER, 0);
        supply.observe(new AudioRuntime.Stamp(0, 800_000_000), 800_000_000, 48000);
        supply.observe(new AudioRuntime.Stamp(4800, 900_000_000), 900_000_000, 48000);
        assert supply.budget() == 48000;
        assert supply.replenish(900_000_000) == 48000;
        assert supply.replenish(999_999_999) == 0;
        try { supply.replenish(1_000_000_000); throw new AssertionError("deadline extended"); }
        catch (IllegalStateException expected) { }
        try { new AudioStartupSupply(48000, 0); throw new AssertionError("oversized buffer accepted"); }
        catch (IllegalArgumentException expected) { }
        var invalid = new AudioStartupSupply(BUFFER, 0);
        int initialBudget = invalid.budget();
        invalid.observe(new AudioRuntime.Stamp(0, 0), 0, 48000);
        invalid.observe(new AudioRuntime.Stamp(40000, 900_000_000), 100_000_000, 48000); // future
        invalid.observe(new AudioRuntime.Stamp(50000, 900_000_000), 900_000_000, 48000); // beyond written
        invalid.observe(new AudioRuntime.Stamp(20000, 900_000_000), 900_000_000, 48000); // bad rate
        assert invalid.budget() == initialBudget;
        invalid.observe(new AudioRuntime.Stamp(20000, 900_000_000), 950_000_000, 48000); // no progress
        assert invalid.budget() == initialBudget;
        System.out.println("PASS absent clock/underrun stops before 1s, 48000 absolute cap, invalid/frozen/rate-bad samples cannot extend allowance");
    }
    public static void main(String[] args) throws Exception {
        run(480, Integer.MAX_VALUE, true);
        run(512, 401, true);
        failures();
    }
}
