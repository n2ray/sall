package io.github.n2ray.sall;

final class TextScale {
    private TextScale() {}
    record Overlay(boolean appsHidden, boolean menuHidden, boolean followingHidden, boolean stripDimmed) {}
    static Overlay overlay(boolean open, boolean landscape, boolean menuInBar) {
        return new Overlay(open && !landscape, open && menuInBar, open, open && landscape);
    }
    static boolean offersOff(Boolean setting, Boolean running) {
        return running != null ? !running : !Boolean.TRUE.equals(setting);
    }
    static double position(Double scale, boolean off) { return scale == null ? (off ? .25 : 1) : scale; }
    static double snap(double value, boolean off) {
        return Math.max(off ? .25 : .5, Math.min(1.5, Math.floor(value * 4 + .5) / 4));
    }
    static Double scale(double position) { return position == .25 ? null : position; }
    static boolean singlePointer(int pointers) { return pointers == 1; }
    static String title(double position) {
        if (position == .25) return "Off";
        String number = String.format(java.util.Locale.ROOT, "%.2f", position).replaceAll("0+$", "").replaceAll("\\.$", "");
        return number + "×";
    }
}
