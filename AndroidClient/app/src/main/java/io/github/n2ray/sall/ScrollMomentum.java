package io.github.n2ray.sall;

/** The same 60 Hz exponential coast used by the iOS trackpad. */
final class ScrollMomentum {
    private double x, y;
    boolean start(double velocityX, double velocityY, double density) {
        if (!Double.isFinite(velocityX) || !Double.isFinite(velocityY) || !Double.isFinite(density) || density <= 0
                || Math.hypot(velocityX, velocityY) <= 150 * density) { x = y = 0; return false; }
        x = velocityX; y = velocityY; return true;
    }
    double firstX() { return x / 60; }
    double firstY() { return y / 60; }
    /** Advance by elapsed seconds, returning whether another delta is visible. */
    boolean advance(double elapsed, double density) {
        if (!Double.isFinite(elapsed) || elapsed <= 0 || !Double.isFinite(density) || density <= 0) return false;
        double decay = Math.pow(.96, Math.min(elapsed, .1) * 60);
        x *= decay; y *= decay;
        return Math.hypot(x, y) / 60 >= .3 * density;
    }
    double stepX(double elapsed) { return x * Math.min(elapsed, .1); }
    double stepY(double elapsed) { return y * Math.min(elapsed, .1); }
    void clear() { x = y = 0; }
}
