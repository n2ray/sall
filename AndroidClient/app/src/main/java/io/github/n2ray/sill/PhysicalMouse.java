package io.github.n2ray.sill;

/** Tracks supported Sill buttons across Android mouse event variants. */
final class PhysicalMouse {
    interface Listener { void pointer(String action, float x, float y); }
    private static final int LEFT = 1, RIGHT = 2;
    private final Listener listener;
    private int held, suppressed;
    private boolean blocked;
    private float x, y;

    PhysicalMouse(Listener listener) { this.listener = listener; }
    boolean held() { return held != 0; }
    void setBlocked(boolean blocked) {
        if (blocked && !this.blocked) cancel();
        this.blocked = blocked;
    }

    void update(int buttons, float x, float y, boolean inside) {
        if (blocked) return;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        this.x = Math.max(0, Math.min(1, x)); this.y = Math.max(0, Math.min(1, y));
        int buttonsNow = buttons & (LEFT | RIGHT);
        suppressed &= buttonsNow;
        if (held != 0) suppressed |= buttonsNow & ~held;
        int next = buttonsNow & ~suppressed;
        // The host exposes one shared modifier state, so ignore simultaneous extra buttons.
        if (held == 0 && next == (LEFT | RIGHT)) { suppressed |= RIGHT; next = LEFT; }
        if (!inside) next &= held;
        if (inside || held != 0) listener.pointer("move", this.x, this.y);
        change(LEFT, "left", next); change(RIGHT, "right", next);
        held = next;
    }
    private void change(int button, String name, int next) {
        if ((held & button) != (next & button))
            listener.pointer(name + ((next & button) != 0 ? "Down" : "Up"), x, y);
    }
    void cancel() { update(0, x, y, false); }
}
