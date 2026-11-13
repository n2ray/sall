package io.github.n2ray.sill;

final class MacMenuRow {
    record NoteText(String title, String subtitle) { }
    static NoteText noteText(String value) {
        String text = value == null ? "" : value;
        boolean period = text.endsWith(").");
        String body = period ? text.substring(0, text.length() - 1) : text;
        int open = body.lastIndexOf(" (");
        if (body.endsWith(")") && open > 0 && open + 2 < body.length() - 1) {
            String title = body.substring(0, open) + (period ? "." : "");
            return new NoteText(title, body.substring(open + 2, body.length() - 1));
        }
        return new NoteText(text, null);
    }
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
