package io.github.n2ray.sill;

final class MacMenuRow {
    static boolean selectable(boolean separator, boolean enabled, String id, String title) {
        return !separator && enabled && id != null && !id.isEmpty() && title != null && !title.trim().isEmpty();
    }

    static String label(String title, String key, String mark, boolean submenu) {
        String value = mark == null ? "" : mark.trim();
        String prefix = value.isEmpty() ? "   " : value.equals("-") || value.equals("–") ? "−  " : "✓  ";
        String suffix = submenu ? "  ›" : key == null || key.isEmpty() ? "" : "   " + key;
        return prefix + title + suffix;
    }
}
