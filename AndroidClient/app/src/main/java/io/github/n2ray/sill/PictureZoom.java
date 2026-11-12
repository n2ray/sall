package io.github.n2ray.sill;

/** Local picture magnification; offsets are clamped so no empty area is exposed by panning. */
final class PictureZoom {
    private float scale = 1, x, y, paneWidth, paneHeight;
    void reset() { scale = 1; x = y = 0; }
    boolean isZoomed() { return scale > 1; }
    void settle() { if (scale < 1.02f) reset(); }
    PictureZoom copy() { PictureZoom z = new PictureZoom(); z.scale = scale; z.x = x; z.y = y; z.paneWidth = paneWidth; z.paneHeight = paneHeight; return z; }
    void restore(PictureZoom z) { scale = z.scale; x = z.x; y = z.y; paneWidth = z.paneWidth; paneHeight = z.paneHeight; }
    private void resize(float width, float height) {
        if (paneWidth > 0 && paneHeight > 0 && (paneWidth != width || paneHeight != height)) {
            x *= width / paneWidth; y *= height / paneHeight;
        }
        paneWidth = width; paneHeight = height;
    }
    FrameGeometry.Rect frame(float width, float height, float videoWidth, float videoHeight) {
        resize(width, height);
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
        float maxX = width * (next - 1) / 2, maxY = height * (next - 1) / 2;
        x = Math.max(-maxX, Math.min(maxX, x)); y = Math.max(-maxY, Math.min(maxY, y));
        scale = next;
    }
}
