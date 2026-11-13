package io.github.n2ray.sall;

/** A visible stream may hold the screen awake for ten minutes after a person's last action. */
final class SessionIdlePolicy {
    static final long LIMIT_MS = 10 * 60 * 1000L;
    static final long NO_HOLD = -1;

    static long holdUntil(boolean foreground, boolean streaming, long lastAction, long now) {
        if (!foreground || !streaming || lastAction < 0 || now < lastAction) return NO_HOLD;
        long elapsed = now - lastAction;
        if (elapsed >= LIMIT_MS) return NO_HOLD;
        return now > Long.MAX_VALUE - (LIMIT_MS - elapsed) ? Long.MAX_VALUE : now + LIMIT_MS - elapsed;
    }
}
