package io.github.n2ray.sill;

/** Older hosts omit display counts; only step when the current desktop is known. */
final class DesktopNavigation {
    static boolean next(boolean active, int count, int current) {
        return active && count >= 2 && current >= 1 && current <= count;
    }
}
