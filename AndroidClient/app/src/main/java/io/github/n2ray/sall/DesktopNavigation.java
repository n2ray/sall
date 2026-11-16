package io.github.n2ray.sall;

/** Older hosts omit display counts; only step when the current desktop is known. */
final class DesktopNavigation {
    static boolean next(boolean active, int count, int current) {
        return active && count >= 2 && current >= 1 && current <= count;
    }
    static int dots(boolean active, int count, int current) {
        return next(active, count, current) && count <= 6 ? count : 0;
    }
    static String counter(boolean active, int count, int current) {
        return next(active, count, current) && count > 6 ? current + "/" + count : null;
    }
}
