package io.github.n2ray.sall;

/** Aspect-fit video geometry shared by touch and the visible cursor. */
final class FrameGeometry {
    record Rect(float left, float top, float width, float height) {
        float x(float coordinate) { return clamp((coordinate - left) / width); }
        float y(float coordinate) { return clamp((coordinate - top) / height); }
        private static float clamp(float n) { return Math.max(0, Math.min(1, n)); }
    }
    static Rect fit(float width, float height, float videoWidth, float videoHeight) {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) throw new IllegalArgumentException("Invalid dimensions");
        float scale = Math.min(width / videoWidth, height / videoHeight);
        float w = videoWidth * scale, h = videoHeight * scale;
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
}
