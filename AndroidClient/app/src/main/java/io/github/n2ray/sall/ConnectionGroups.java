package io.github.n2ray.sall;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local grouping is keyed by the complete certificate fingerprint, never a route or short ID. */
final class ConnectionGroups {
    static final List<String> PRESETS = List.of("Home", "Work", "Personal");
    private static final java.util.regex.Pattern CHARACTER = java.util.regex.Pattern.compile("\\X");
    static String clean(String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length();) {
            int cp = raw.codePointAt(i); i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { if (out.length() > 0) space = true; continue; }
            if (Character.isISOControl(cp)) return null;
            if (space) out.append(' ');
            space = false;
            out.appendCodePoint(cp);
        }
        java.util.regex.Matcher characters = CHARACTER.matcher(out);
        int end = 0;
        for (int count = 0; count < 32 && characters.find(); count++) end = characters.end();
        String value = out.substring(0, end).trim();
        if (value.isEmpty() || value.equalsIgnoreCase("Other")) return null;
        for (String preset : PRESETS) if (preset.equalsIgnoreCase(value)) return preset;
        return value;
    }
    static String canonical(String raw, Iterable<String> existing) {
        String value = clean(raw); if (value == null) return null;
        String first = null;
        for (String item : existing) {
            String candidate = clean(item);
            if (candidate != null && candidate.equalsIgnoreCase(value) && (first == null || candidate.compareTo(first) < 0)) first = candidate;
        }
        return first == null ? value : first;
    }
    static List<String> ordered(Iterable<String> values, boolean presets) {
        List<String> result = new ArrayList<>();
        if (presets) result.addAll(PRESETS);
        for (String raw : values) {
            String name = canonical(raw, result);
            if (name != null && result.stream().noneMatch(v -> v.equalsIgnoreCase(name))) result.add(name);
        }
        result.sort(Comparator.comparingInt((String v) -> { int i = PRESETS.indexOf(v); return i < 0 ? PRESETS.size() : i; })
                .thenComparing(String.CASE_INSENSITIVE_ORDER).thenComparing(Comparator.naturalOrder()));
        return result;
    }
    static <T> Map<String, List<T>> sections(List<T> items, java.util.function.Function<T, String> group) {
        Map<String, List<T>> byName = new LinkedHashMap<>(); List<T> other = new ArrayList<>();
        for (T item : items) {
            String name = canonical(group.apply(item), byName.keySet());
            if (name == null) other.add(item); else byName.computeIfAbsent(name, ignored -> new ArrayList<>()).add(item);
        }
        Map<String, List<T>> sections = new LinkedHashMap<>();
        if (byName.isEmpty()) { if (!items.isEmpty()) sections.put("", List.copyOf(items)); return sections; }
        for (String name : ordered(byName.keySet(), false)) sections.put(name, List.copyOf(byName.get(name)));
        if (!other.isEmpty()) sections.put("Other", List.copyOf(other));
        return sections;
    }
    static boolean canEdit(boolean running, boolean stillSaved) { return !running && stillSaved; }
}
