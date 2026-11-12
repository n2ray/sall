package io.github.n2ray.sill;

/** iOS portrait uses a 16:10 pane unless a short screen needs the space for its trackpad. */
final class PhoneStreamLayout {
    static int pictureHeight(int width, int height, int fixedHeight, int minTrackpad) {
        if (width <= 0 || height <= 0) return 0;
        int natural = (int) Math.floor(width / 1.6);
        return Math.min(natural, Math.max(0, height - Math.max(0, fixedHeight) - Math.max(0, minTrackpad)));
    }
}
