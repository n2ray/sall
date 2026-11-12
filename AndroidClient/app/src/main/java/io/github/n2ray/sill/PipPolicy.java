package io.github.n2ray.sill;

/** Single-session counterpart of the iOS picture-in-picture lifecycle. */
final class PipPolicy {
    static final long PAUSE_LIMIT_MS = 600_000;
    private boolean active, paused;
    private long pausedAt;
    record Aspect(int width, int height) { }
    static Aspect aspect(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid video size");
        if ((long) width * 239 < (long) height * 100) return new Aspect(100, 239);
        if ((long) width * 100 > (long) height * 239) return new Aspect(239, 100);
        return new Aspect(width, height);
    }
    static boolean qualifies(boolean enabled, boolean supported, boolean connected,
                             boolean streaming, boolean pictureCame, boolean covered) {
        return enabled && supported && connected && streaming && pictureCame && !covered;
    }
    static boolean maySend(boolean active, int kind, boolean release) {
        boolean deviceEvent = kind == 6 || kind == 7 || kind == 8 || kind == 9 || kind == 15
                || kind == 17 || kind == 24 || kind == 25 || kind == 28 || kind == 32 || kind == 47;
        return !active || !deviceEvent || release;
    }
    synchronized void started() { if (!active) { active = true; paused = false; } }
    synchronized void ended() { active = paused = false; pausedAt = 0; }
    synchronized void setPlaying(boolean playing, long now) {
        if (!active) return;
        if (!playing && !paused) pausedAt = now;
        paused = !playing;
    }
    synchronized boolean active() { return active; }
    synchronized boolean paused() { return active && paused; }
    synchronized boolean watched(boolean foreground) { return foreground || (active && !paused); }
    synchronized boolean pauseExpired(long now) {
        return active && paused && now >= pausedAt && now - pausedAt >= PAUSE_LIMIT_MS;
    }
}
