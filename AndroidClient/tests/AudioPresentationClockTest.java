package io.github.n2ray.sall;

public final class AudioPresentationClockTest {
    public static void main(String[] args) {
        long start = 1_000_000_000L;
        var clock = new AudioPresentationClock(start);
        long g = clock.generation();
        int silence = 0;
        for (int i = 0; i < 1000; i++) silence += clock.startupSilence(g, start + i * 500_000);
        assert silence == 12000;
        assert clock.reading() == null;
        g = clock.reset(start);
        clock.observe(g, 0, start, start, 1000);
        assert clock.observe(g, 48, start + 1_000_000, start + 1_000_000, 1000) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 96, start + 2_000_000, start + 2_000_000, 1000) == AudioPresentationClock.State.STARTING;
        assert clock.reading() == null; // Three good points still require the minimum span.
        assert clock.check(start + 1_000_000_000L) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        assert clock.observe(g, 0, start, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 2400, start + 50_000_000, start + 50_000_000, 4800) == AudioPresentationClock.State.STARTING;
        assert clock.reading() == null;
        assert clock.observe(g, 4800, start + 100_000_000, start + 100_000_000, 4800) == AudioPresentationClock.State.READY;
        assert clock.reading().frame() == 4800;
        assert clock.startupSilence(g, start + 101_000_000) == 0;
        assert clock.observe(g, 4800, start + 100_000_000, start + 200_000_000, 4800) == AudioPresentationClock.State.READY;
        assert clock.check(start + 351_000_000) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        clock.observe(g, 0, start, start, 10000);
        clock.observe(g, 2400, start + 50_000_000, start + 50_000_000, 10000);
        clock.observe(g, 4800, start + 100_000_000, start + 100_000_000, 10000);
        assert clock.observe(g, 6720, start + 145_100_000, start + 145_100_000, 10000) == AudioPresentationClock.State.READY;
        assert clock.observe(g, 8640, start + 185_100_000, start + 185_100_000, 10000) == AudioPresentationClock.State.READY;
        assert clock.observe(g, 9600, start + 220_100_000, start + 220_100_000, 10000) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        assert clock.observe(g - 1, 0, start, start, 0) == AudioPresentationClock.State.STALE;
        assert clock.observe(g, 481, start, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 0, start - 1, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.reading() == null;
        // Keep all 64 bits; a valid long-running track need not fit unsigned 32-bit frames.
        long frame = (1L << 32) + 500;
        clock.observe(g, frame, start, start, frame + 4800);
        clock.observe(g, frame + 2400, start + 50_000_000, start + 50_000_000, frame + 4800);
        assert clock.observe(g, frame + 4800, start + 100_000_000, start + 100_000_000, frame + 4800) == AudioPresentationClock.State.READY;
        assert clock.reading().frame() == frame + 4800;
        assert clock.observe(g, frame - 1, start + 110_000_000, start + 110_000_000, frame + 4800) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        clock.observe(g, 0, start, start, 48000);
        assert clock.observe(g, 48000, start + 10_000_000, start + 10_000_000, 60000) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 50400, start + 60_000_000, start + 60_000_000, 60000) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 52800, start + 110_000_000, start + 110_000_000, 60000) == AudioPresentationClock.State.READY;
        assert clock.observe(g, 60000, start + 120_000_000, start + 120_000_000, 60000) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        clock.observe(g, 0, start, start, 100000);
        for (int i = 1; i < 100; i++) {
            // Every short pair fits 240 frames, but the anchor rejects accumulated bad rate.
            assert clock.observe(g, i * 680, start + i * 10_000_000L, start + i * 10_000_000L, 100000) == AudioPresentationClock.State.STARTING;
        }
        assert clock.check(start + 1_000_000_000L) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        assert clock.startupSilence(g, start) == 960;
        assert clock.startupSilence(g, start + 15_000_000) == 720;
        assert clock.startupSilence(g, start + 15_000_000) == 0;
        assert clock.observe(g, 0, start + 100, start, 0) == AudioPresentationClock.State.STARTING;
        assert clock.reading() == null;
        System.out.println("PASS bounded bootstrap silence, missing/frozen clock deadline, three advancing readings spanning 100ms, transient startup recovery, persistent anchored rate rejection, elapsed-time silence replenishment, stale generation, bounds, full long frames, reset/backward/rate mismatch");
    }
}
