package io.github.n2ray.sill;

/** Per-session counterpart of iOS KeyframeRecovery. Times are monotonic milliseconds. */
final class KeyframeRecovery {
    private boolean open, asked, wentAway;
    private int repeats;
    private long lastAsk = -1, backAt = -1, generation;

    synchronized void interrupted(long now) {
        if (open) return;
        open = true;
        asked = false;
        generation++;
        repeats = !wentAway && backAt >= 0 && now >= backAt && now - backAt < 10_000
                ? Math.min(4, repeats + 1) : 0;
    }

    synchronized boolean consider(long now, boolean active, boolean streaming, long worstRttMs) {
        if (!open || !active || !streaming) return false;
        if (asked) {
            long wait = Math.max(4_000, Math.max(0, Math.min(worstRttMs, 60_000)) * 4);
            if (lastAsk >= 0 && now >= lastAsk && now - lastAsk < wait) return false;
            asked = false;
            repeats = Math.min(4, repeats + 1);
        }
        long spacing = 2_000L << repeats;
        if (lastAsk >= 0 && now >= lastAsk && now - lastAsk < spacing) return false;
        asked = true;
        lastAsk = now;
        return true;
    }

    synchronized void pictureBack(long now, boolean active, long expectedGeneration) {
        if (!open || generation != expectedGeneration) return;
        open = false;
        backAt = now;
        if (active) wentAway = false;
    }

    synchronized void leftForeground() { wentAway = true; asked = false; repeats = 0; }
    synchronized boolean isOpen() { return open; }
    synchronized long generation() { return generation; }
}
