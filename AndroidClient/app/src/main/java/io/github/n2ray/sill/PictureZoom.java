package io.github.n2ray.sill;

/** Local picture magnification; offsets are clamped so no empty area is exposed by panning. */
final class PictureZoom {
    private float scale = 1, x, y;
    void reset() { scale = 1; x = y = 0; }
    FrameGeometry.Rect frame(float width, float height, float videoWidth, float videoHeight) {
        FrameGeometry.Rect fit = FrameGeometry.fit(width, height, videoWidth, videoHeight);
        float w = fit.width() * scale, h = fit.height() * scale;
        return new FrameGeometry.Rect((width - w) / 2 + x, (height - h) / 2 + y, w, h);
    }
    void change(float factor, float focusX, float focusY, float dx, float dy,
                float width, float height, float videoWidth, float videoHeight) {
        if (!Float.isFinite(factor) || factor <= 0) return;
        FrameGeometry.Rect old = frame(width, height, videoWidth, videoHeight);
        float next = Math.max(1, Math.min(5, scale * factor));
        float ratio = next / scale;
        float w = old.width() * ratio, h = old.height() * ratio;
        x = focusX - (focusX - old.left()) * ratio + dx - (width - w) / 2;
        y = focusY - (focusY - old.top()) * ratio + dy - (height - h) / 2;
        x = Math.max(-Math.max(0, (w - width) / 2), Math.min(Math.max(0, (w - width) / 2), x));
        y = Math.max(-Math.max(0, (h - height) / 2), Math.min(Math.max(0, (h - height) / 2), y));
        scale = next;
    }
}
