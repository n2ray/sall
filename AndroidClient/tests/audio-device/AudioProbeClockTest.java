package io.github.n2ray.sall;

public final class AudioProbeClockTest {
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }

    public static void main(String[] args) {
        long start = 1_000_000_000L;
        AudioPresentationClock clock = new AudioPresentationClock(start);
        AudioPresentationClock beforeClock = new AudioPresentationClock(start);
        AudioPresentationClock.State state = AudioPresentationClock.State.STARTING;
        for (int i = 0; i < 3; i++) {
            long stamp = start + 1_000_000L + i * 50_000_000L;
            long before = stamp - 1, after = stamp + 1, frame = i * 2400L;
            state = AudioProbeClock.sample(clock, true, frame, stamp, before, after, 10000);
            beforeClock.observe(beforeClock.generation(), frame, stamp, before, 10000);
        }
        require(state == AudioPresentationClock.State.READY && clock.reading().frame() == 4800);
        require(beforeClock.reading() == null); // A pre-call sample rejects timestamps produced during the call.

        AudioPresentationClock future = new AudioPresentationClock(start);
        require(AudioProbeClock.sample(future, true, 0, start + 30, start + 10, start + 20, 100)
                == AudioPresentationClock.State.STARTING);
        require(future.reading() == null);
        require(AudioProbeClock.sample(future, false, 0, 0, start + 1_000_000_000L, start + 1_000_000_001L, 100)
                == AudioPresentationClock.State.FAILED);
        try {
            AudioProbeClock.sample(clock, false, 0, 0, start + 2, start + 1, 10000);
            throw new AssertionError("accepted backwards local clock");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Audio probe uses post-call monotonic time for production clock observations");
    }
}
