package io.github.n2ray.sill;

import java.util.ArrayList;
import java.text.Collator;
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

    static boolean sourceChanged(long before, boolean beforeDesktop, long after, boolean afterDesktop) {
        return (after >= 0 || afterDesktop) && (before != after || beforeDesktop != afterDesktop);
    }

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
            if (matches(name, needle)) open.add(new Group(name, safe(all.get(0).bundleId()), java.util.Collections.unmodifiableList(all)));
        }
        List<App> closed = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (App app : apps) {
            if (app == null || app.bundleId() == null || app.bundleId().isEmpty() || grouped.containsKey(app.bundleId())
                    || !seen.add(app.bundleId()) || !matches(app.name(), needle)) continue;
            closed.add(app);
        }
        Collator collator = Collator.getInstance();
        closed.sort((a, b) -> collator.compare(safe(a.name()), safe(b.name())));
        return new Results(java.util.Collections.unmodifiableList(open), java.util.Collections.unmodifiableList(closed));
    }
    static long nextWindow(Group group, long active) {
        List<Window> windows = group.windows();
        if (windows.isEmpty()) return -1;
        for (int i = 0; i < windows.size(); i++)
            if (windows.get(i).id() == active) return windows.get((i + 1) % windows.size()).id();
        return windows.get(0).id();
    }
    private static String safe(String text) { return text == null ? "" : text; }
    private static boolean matches(String text, String needle) { return safe(text).toLowerCase(Locale.ROOT).contains(needle); }
}
