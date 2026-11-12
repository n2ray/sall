package io.github.n2ray.sill;

final class WindowHold {
    private float x, y;
    private boolean active, moved, held;
    void begin(float x, float y) { this.x = x; this.y = y; active = true; moved = false; held = false; }
    boolean move(float x, float y, float limit) {
        if (Math.hypot(x - this.x, y - this.y) > limit) moved = true;
        return moved;
    }
    boolean hold() { if (!active || moved) return false; held = true; return true; }
    boolean isHeld() { return active && held; }
    boolean end() { boolean tap = active && !moved && !held; active = false; return tap; }
    void cancel() { active = false; }
}
