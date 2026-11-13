package io.github.n2ray.sill;

/** Mirrors the runtime's timestamp sampling boundary without depending on Android APIs. */
final class AudioProbeClock {
    static AudioPresentationClock.State sample(AudioPresentationClock clock, boolean available,
                                               long frame, long stampNanos, long before, long after, long writtenFrames) {
        if (after < before) throw new IllegalArgumentException("monotonic time moved backwards");
        AudioPresentationClock.State state = clock.check(after);
        if (available && stampNanos > 0 && frame >= 0)
            state = clock.observe(clock.generation(), frame, stampNanos, after, writtenFrames);
        return state;
    }
}
