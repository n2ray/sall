package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A snapshot grouped in the Mac's window order, with unopened apps sorted by name. */
final class AppDrawerModel {
    record Window(long id, String title, String appName, String bundleId) { }
    record App(String name, String bundleId) { }
    record Group(String name, String bundleId, List<Window> windows) { }
    record Results(List<Group> open, List<App> closed) { }

    static Results filter(List<Window> windows, List<App> apps, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, List<Window>> grouped = new LinkedHashMap<>();
        for (Window window : windows) {
            if (window == null || window.id() < 0) continue;
            String key = safe(window.bundleId()).isEmpty() ? "\u0000" + safe(window.appName()) : window.bundleId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(window);
        }
        List<Group> open = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<Window> all = entry.getValue();
            String name = all.get(0).appName();
            List<Window> visible = new ArrayList<>();
            for (Window window : all)
                if (matches(name, needle) || matches(window.title(), needle)) visible.add(window);
            if (!visible.isEmpty()) open.add(new Group(name, safe(all.get(0).bundleId()), java.util.Collections.unmodifiableList(visible)));
        }
        List<App> closed = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (App app : apps) {
            if (app == null || app.bundleId() == null || app.bundleId().isEmpty() || grouped.containsKey(app.bundleId())
                    || !seen.add(app.bundleId()) || !matches(app.name(), needle)) continue;
            closed.add(app);
        }
        closed.sort(Comparator.comparing(app -> safe(app.name()).toLowerCase(Locale.ROOT)));
        return new Results(java.util.Collections.unmodifiableList(open), java.util.Collections.unmodifiableList(closed));
    }
    private static String safe(String text) { return text == null ? "" : text; }
    private static boolean matches(String text, String needle) { return safe(text).toLowerCase(Locale.ROOT).contains(needle); }
}
