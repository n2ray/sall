package io.github.n2ray.sall;

/** One conservative fit estimate from an active window's settled size in Mac points. */
final class WindowAutoFit {
    private WindowAutoFit() {}

    static Double next(double panelWidth, double panelHeight, double windowWidth, double windowHeight, double scale) {
        if (!valid(panelWidth, 16384) || !valid(panelHeight, 16384)
                || !valid(windowWidth, 65536) || !valid(windowHeight, 65536)
                || !Double.isFinite(scale) || scale < .25 || scale > 4) return null;
        double widthRatio = windowWidth * scale / panelWidth;
        double heightRatio = windowHeight * scale / panelHeight;
        double larger = Math.max(widthRatio, heightRatio);
        double smaller = Math.min(widthRatio, heightRatio);
        if (larger <= 1.02 || larger / smaller <= 1.02) return null;
        boolean widthTaken = Math.abs(windowWidth * scale - panelWidth) <= Math.max(3, panelWidth * .02);
        boolean heightTaken = Math.abs(windowHeight * scale - panelHeight) <= Math.max(3, panelHeight * .02);
        if (!widthTaken && !heightTaken && (widthRatio <= 1 || heightRatio <= 1)) return null;
        double next = Math.max(.25, Math.min(scale, scale / larger));
        return next < scale * .98 && scale - next >= .01 ? next : null;
    }

    static boolean matchingFrame(double windowWidth, double windowHeight, double frameWidth, double frameHeight) {
        if (!valid(windowWidth, 65536) || !valid(windowHeight, 65536)
                || !valid(frameWidth, 65536) || !valid(frameHeight, 65536)) return false;
        double ratio = (frameWidth / frameHeight) / (windowWidth / windowHeight);
        return ratio >= .98 && ratio <= 1.02;
    }

    private static boolean valid(double value, double maximum) {
        return Double.isFinite(value) && value > 0 && value <= maximum;
    }
}
