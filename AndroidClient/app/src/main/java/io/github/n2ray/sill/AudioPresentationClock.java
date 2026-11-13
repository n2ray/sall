package io.github.n2ray.sill;

/** Validates presentation timestamps in System.nanoTime's timebase, never playback-head estimates.
 * This is a device-adapter contract, not proof that a device's timestamp measures acoustic output.
 * The owner supplies AudioTimestamp's full long framePosition and nanoTime after each physical
 * output reset. Silence counted here must be enqueued through the same AudioOutputTimeline.
 */
final class AudioPresentationClock {
    static final int RATE = 48000, STARTUP_FRAMES = 12000, CHUNK_FRAMES = 240;
    static final long STARTUP_TIMEOUT_NS = 1_000_000_000L, STALE_NS = 250_000_000L;
    enum State { STARTING, READY, FAILED, STALE }
    record Reading(long frame, long nanos) {}
    private long generation, started, priorFrame, priorNanos, lastProgress;
    private int startupFrames;
    private boolean havePrior;
    private State state = State.STARTING;
    private Reading reading;

    AudioPresentationClock(long now) { reset(now); }
    long generation() { return generation; }
    State state() { return state; }
    Reading reading() { return state == State.READY ? reading : null; }
    long reset(long now) {
        generation = Math.incrementExact(generation);
        started = lastProgress = now; startupFrames = 0; havePrior = false;
        priorFrame = priorNanos = 0; reading = null; state = State.STARTING;
        return generation;
    }
    /** Returns one bounded bootstrap chunk. No invented mapping when hardware gives no timestamp.
     * Repeated calls cannot allocate beyond 250 ms. Zero may mean wait, READY, or FAILED.
     */
    int startupSilence(long token, long now) {
        if (token != generation || check(now) != State.STARTING) return 0;
        int frames = Math.min(CHUNK_FRAMES, STARTUP_FRAMES - startupFrames);
        startupFrames += frames;
        return frames;
    }
    State check(long now) {
        if (state == State.FAILED) return state;
        if (now < started || (state == State.STARTING && now - started >= STARTUP_TIMEOUT_NS)
                || (state == State.READY && now - lastProgress > STALE_NS)) {
            state = State.FAILED; reading = null;
        }
        return state;
    }
    State observe(long token, long frame, long stampNanos, long now, long writtenFrames) {
        if (token != generation) return State.STALE;
        if (check(now) == State.FAILED) return state;
        // A newly started track can return an old or temporarily unavailable timestamp.
        if (frame < 0 || stampNanos < started || stampNanos > now
                || now - stampNanos > STALE_NS || frame > writtenFrames) return state;
        if (havePrior) {
            if (frame < priorFrame || stampNanos < priorNanos) return fail();
            if (frame == priorFrame || stampNanos == priorNanos) return state;
            double elapsedFrames = (stampNanos - priorNanos) / 1e9 * RATE;
            // Timestamp progression must fit its sample clock; allow 5 ms observation jitter.
            if (Math.abs((frame - priorFrame) - elapsedFrames) > CHUNK_FRAMES) return fail();
            state = State.READY;
        }
        havePrior = true; priorFrame = frame; priorNanos = stampNanos;
        lastProgress = now; reading = new Reading(frame, stampNanos);
        return state;
    }
    private State fail() { reading = null; return state = State.FAILED; }
}
