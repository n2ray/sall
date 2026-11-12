package io.github.n2ray.sill;

import java.util.concurrent.atomic.AtomicLong;

/** Reject reports made before the Mac read every input already queued on this connection. */
final class PointerSync {
    private final AtomicLong inputs = new AtomicLong();

    void inputQueued() { inputs.incrementAndGet(); }

    boolean accepts(long seen, boolean inside, double x, double y) {
        return seen >= inputs.get() && inside && Double.isFinite(x) && Double.isFinite(y)
                && x >= 0 && x <= 1 && y >= 0 && y <= 1;
    }
}
