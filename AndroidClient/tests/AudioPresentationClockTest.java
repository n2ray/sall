package io.github.n2ray.sill;

public final class AudioPresentationClockTest {
    public static void main(String[] args) {
        long start = 1_000_000_000L;
        var clock = new AudioPresentationClock(start);
        long g = clock.generation();
        int silence = 0;
        for (int i = 0; i < 1000; i++) silence += clock.startupSilence(g, start + i * 1000);
        assert silence == 12000;
        assert clock.reading() == null;
        assert clock.check(start + 1_000_000_000L) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        assert clock.observe(g, 0, start, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 480, start + 10_000_000, start + 10_000_000, 480) == AudioPresentationClock.State.READY;
        assert clock.reading().frame() == 480;
        assert clock.startupSilence(g, start + 11_000_000) == 0;
        assert clock.observe(g, 480, start + 10_000_000, start + 200_000_000, 480) == AudioPresentationClock.State.READY;
        assert clock.check(start + 261_000_000) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        assert clock.observe(g - 1, 0, start, start, 0) == AudioPresentationClock.State.STALE;
        assert clock.observe(g, 481, start, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.observe(g, 0, start - 1, start, 480) == AudioPresentationClock.State.STARTING;
        assert clock.reading() == null;
        // Keep all 64 bits; a valid long-running track need not fit unsigned 32-bit frames.
        long frame = (1L << 32) + 500;
        clock.observe(g, frame, start, start, frame + 480);
        assert clock.observe(g, frame + 480, start + 10_000_000, start + 10_000_000, frame + 480) == AudioPresentationClock.State.READY;
        assert clock.reading().frame() == frame + 480;
        assert clock.observe(g, frame - 1, start + 20_000_000, start + 20_000_000, frame + 480) == AudioPresentationClock.State.FAILED;
        g = clock.reset(start);
        clock.observe(g, 0, start, start, 48000);
        assert clock.observe(g, 48000, start + 10_000_000, start + 10_000_000, 48000) == AudioPresentationClock.State.FAILED;
        System.out.println("PASS bounded bootstrap silence, missing/frozen clock deadline, two advancing readings, stale generation, bounds, full long frames, reset/backward/rate mismatch");
    }
}
