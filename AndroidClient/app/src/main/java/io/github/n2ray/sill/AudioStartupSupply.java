package io.github.n2ray.sill;

/** Serial-owner, one-generation silent acquisition allowance. It never grants clock trust. */
final class AudioStartupSupply {
    static final int MAX_FRAMES = 48000;
    private final int bufferFrames;
    private final long started;
    private long priorFrame = -1, priorStamp;
    private int budget, allocated;
    private boolean measured;

    AudioStartupSupply(int bufferFrames, long started) {
        if (bufferFrames < 1 || bufferFrames >= MAX_FRAMES) throw new IllegalArgumentException("Unsupported audio buffer size");
        this.bufferFrames = bufferFrames; this.started = started;
        budget = Math.min(MAX_FRAMES, bufferFrames + 12000);
    }
    int budget() { return budget; }
    int allocated() { return allocated; }
    void observe(AudioRuntime.Stamp stamp, long now, long writtenFrames) {
        if (measured || stamp == null || now < started || now - started >= AudioPresentationClock.STARTUP_TIMEOUT_NS
                || stamp.frame() < 0 || stamp.frame() > writtenFrames || stamp.nanos() < started
                || stamp.nanos() > now || now - stamp.nanos() > AudioPresentationClock.STALE_NS) return;
        if (priorFrame >= 0 && stamp.frame() > priorFrame && stamp.nanos() > priorStamp
                && Math.abs((stamp.frame() - priorFrame) - (stamp.nanos() - priorStamp) / 1e9 * 48000)
                <= AudioPresentationClock.CHUNK_FRAMES) {
            long allowanceNanos = now - started + AudioPresentationClock.STABLE_SPAN_NS + 40_000_000L;
            long frames = (allowanceNanos * 48000 + 999_999_999L) / 1_000_000_000L;
            budget = (int) Math.min(MAX_FRAMES, Math.max(budget, bufferFrames + frames));
            measured = true;
        }
        priorFrame = stamp.frame(); priorStamp = stamp.nanos();
    }
    int replenish(long now) {
        if (now < started || now - started >= AudioPresentationClock.STARTUP_TIMEOUT_NS)
            throw new IllegalStateException("Startup supply deadline");
        long target = (now - started) * 48000 / 1_000_000_000L + Math.max(960, bufferFrames);
        int frames = (int) Math.max(0, Math.min(budget, target) - allocated);
        allocated += frames;
        return frames;
    }
}
