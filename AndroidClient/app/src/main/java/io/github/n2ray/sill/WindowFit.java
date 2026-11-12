package io.github.n2ray.sill;

/** A smaller text scale gives an app more Mac points to satisfy its minimum window size. */
final class WindowFit {
    static double next(double scale, double panelWidth, double panelHeight, double videoWidth, double videoHeight,
                       double windowWidth, double windowHeight) {
        if (!Double.isFinite(scale) || !Double.isFinite(panelWidth) || !Double.isFinite(panelHeight)
                || !Double.isFinite(videoWidth) || !Double.isFinite(videoHeight)
                || !Double.isFinite(windowWidth) || !Double.isFinite(windowHeight)
                || scale <= 0 || panelWidth <= 0 || panelHeight <= 0 || videoWidth <= 0 || videoHeight <= 0
                || windowWidth <= 0 || windowHeight <= 0) return scale;
        double targetWidth = panelWidth / scale, targetHeight = panelHeight / scale;
        if (windowWidth < targetWidth * .85 || windowHeight < targetHeight * .85) return scale;
        if (Math.abs(windowWidth / windowHeight / (videoWidth / videoHeight) - 1) > .12) return scale;
        if (Math.abs(windowWidth / targetWidth - 1) > .12 && Math.abs(windowHeight / targetHeight - 1) > .12) return scale;
        double ratio = (videoWidth / videoHeight) / (panelWidth / panelHeight);
        double mismatch = Math.min(ratio, 1 / ratio);
        if (mismatch >= .94 || scale <= .25) return scale;
        double next = Math.max(.25, scale * mismatch * .98);
        return scale - next < .01 ? scale : next;
    }
}
