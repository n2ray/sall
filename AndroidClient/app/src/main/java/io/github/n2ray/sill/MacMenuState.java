package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One connection's mirrored Mac menus; call on its owner thread. Tokens and keys must be unique. */
final class MacMenuState {
    static final String CHANGED = "The menus changed. Open the menu again.", NOT_CONNECTED = "Not connected.", NO_ITEMS = "No items";
    static final int MAX_MENUS = 32, MAX_CHOICES = 16;
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    enum Kind { ITEM, SUBMENU, NOTE }
    enum Mark { OFF, ON, MIXED }
    record Item(String id, String title, boolean separator, Boolean enabled, String mark, String key, boolean submenu) { }
    record Row(Kind kind, String id, String title, String key, boolean enabled, Mark mark, Integer version) {
        static Row note(String title) { return new Row(Kind.NOTE, null, title, null, false, Mark.OFF, null); }
    }
    record Content(String message, List<List<Row>> sections, int more) {
        static Content message(String text) { return new Content(text, List.of(), 0); }
        static Content sections(List<List<Row>> sections, int more) { return new Content(null, sections, more); }
    }
    record Done(long key, Content content) { }
    record Refusal(String title, String id, String note, boolean byMac) { }
    record Command(int version, String id, String title, long token) { }
    enum FetchKind { SEND, JOINED, SETTLED }
    record Fetch(FetchKind kind, Command command, Content content) { }
    enum PressKind { SEND, REFUSED }
    record Press(PressKind kind, Command command, Refusal refusal) { }
    record Top(Integer version, String app, List<Row> menus, boolean stale, String note) { }
    record Message(Integer version, String app, List<Item> menus, Long answering, List<Item> items,
                   Integer more, Boolean pressed, boolean stale, String note) { }
    record Received(List<Done> done, boolean topChanged, Refusal refusal) { }
    private static final class Waiting {
        final String id, title; final int version; final long token, sentAt; final List<Long> keys = new ArrayList<>();
        Waiting(String id, String title, int version, long token, long sentAt, long key) {
            this.id = id; this.title = title; this.version = version; this.token = token; this.sentAt = sentAt; keys.add(key);
        }
    }
    private record Choice(long token, String id, String title) { }
    private Integer version;
    private String app, note;
    private boolean stale;
    private List<Row> menus = List.of();
    private final List<Waiting> waiting = new ArrayList<>();
    private final List<Choice> choices = new ArrayList<>();
    Top top() { return new Top(version, app, menus, stale, note); }
    boolean hasMenus() { return !menus.isEmpty() || note != null; }
    Long oldestWait() { return waiting.stream().map(w -> w.sentAt).min(Long::compare).orElse(null); }
    Received receive(Message message) {
        if (message.menus() != null) {
            if (message.version() == null) return new Received(List.of(), false, null);
            Top before = top(); version = message.version(); app = clean(message.app(), 64);
            stale = message.stale(); note = clean(message.note(), 300);
            if (stale && note == null) note = (app == null ? "The app" : app) + " isn’t responding.";
            menus = topRows(message.menus(), version);
            List<Done> done = new ArrayList<>();
            waiting.removeIf(w -> { if (w.version == version) return false; finish(done, w, Content.message(CHANGED)); return true; });
            return new Received(List.copyOf(done), !top().equals(before), null);
        }
        Long token = message.answering();
        if (token == null) return new Received(List.of(), false, null);
        for (int i = 0; i < waiting.size(); i++) {
            Waiting w = waiting.get(i); if (w.token != token) continue;
            waiting.remove(i);
            int more = Math.max(0, message.more() == null ? 0 : message.more());
            Content content = !Objects.equals(message.version(), w.version) ? Content.message(CHANGED)
                    : Content.sections(sections(message.items() == null ? List.of() : message.items(), message.stale(), message.note(), more, w.version), more);
            List<Done> done = new ArrayList<>(); finish(done, w, content);
            return new Received(List.copyOf(done), false, null);
        }
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i); if (choice.token() != token) continue;
            choices.remove(i);
            if (!Boolean.FALSE.equals(message.pressed())) return new Received(List.of(), false, null);
            String reason = clean(message.note(), 300);
            return new Received(List.of(), false, new Refusal(choice.title(), choice.id(), reason == null ? CHANGED : reason, true));
        }
        return new Received(List.of(), false, null);
    }
    Fetch fetch(String id, String title, Integer asked, long key, long token, long now) {
        if (version == null) return new Fetch(FetchKind.SETTLED, null, Content.message(NOT_CONNECTED));
        if (!Objects.equals(asked, version)) return new Fetch(FetchKind.SETTLED, null, Content.message(CHANGED));
        if (id == null || title == null) return new Fetch(FetchKind.SETTLED, null, Content.message(CHANGED));
        if (stale) return new Fetch(FetchKind.SETTLED, null, Content.sections(sections(List.of(), true, note, 0, version), 0));
        for (Waiting w : waiting) if (Objects.equals(w.id, id) && Objects.equals(w.title, title) && w.version == version) {
            w.keys.add(key); return new Fetch(FetchKind.JOINED, null, null);
        }
        waiting.add(new Waiting(id, title, version, token, now, key));
        return new Fetch(FetchKind.SEND, new Command(version, id, title, token), null);
    }
    String barMenuId(String builtId, String title) {
        if (version == null) return null;
        for (Row row : menus) if (Objects.equals(row.id(), builtId) && Objects.equals(row.title(), title)) return row.id();
        for (Row row : menus) if (Objects.equals(row.title(), title)) return row.id();
        return null;
    }
    Press press(Row row, long token) {
        if (version == null || row == null || row.kind() != Kind.ITEM || !row.enabled() || row.id() == null || row.version() == null) return null;
        if (stale) return new Press(PressKind.REFUSED, null, new Refusal(row.title(), row.id(), note == null ? CHANGED : note, false));
        choices.add(new Choice(token, row.id(), row.title()));
        if (choices.size() > MAX_CHOICES) choices.remove(0);
        return new Press(PressKind.SEND, new Command(row.version(), row.id(), row.title(), token), null);
    }
    List<Done> expire(long now, long timeout, String mac) {
        if (timeout < 0) throw new IllegalArgumentException("negative timeout");
        List<Done> done = new ArrayList<>();
        waiting.removeIf(w -> {
            if (now < w.sentAt || now - w.sentAt < timeout) return false;
            finish(done, w, Content.message((mac == null || mac.isEmpty() ? "The Mac" : mac) + " didn’t answer. Open the menu again.")); return true;
        });
        return List.copyOf(done);
    }
    List<Done> reset() { List<Done> done = settleAll(NOT_CONNECTED); version = null; app = note = null; stale = false; menus = List.of(); return done; }
    List<Done> connectionReplaced() { return settleAll(CHANGED); }
    private List<Done> settleAll(String message) {
        List<Done> done = new ArrayList<>(); for (Waiting w : waiting) finish(done, w, Content.message(message));
        waiting.clear(); choices.clear(); return List.copyOf(done);
    }
    private static void finish(List<Done> done, Waiting w, Content content) { for (long key : w.keys) done.add(new Done(key, content)); }
    static List<Row> topRows(List<Item> items, Integer version) {
        List<Row> rows = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (Item item : items) {
            if (rows.size() == MAX_MENUS) break;
            if (item == null || !isTopId(item.id()) || !ids.add(item.id())) continue;
            String title = clean(item.title(), 100);
            if (title == null) { ids.remove(item.id()); continue; }
            rows.add(new Row(Kind.SUBMENU, item.id(), title, null, !Boolean.FALSE.equals(item.enabled()), Mark.OFF, version));
        }
        return List.copyOf(rows);
    }
    static boolean isTopId(String id) {
        if (id == null || id.isEmpty() || id.length() > 4 || id.charAt(0) == '0') return false;
        for (int i = 0; i < id.length(); i++) if (id.charAt(i) < '0' || id.charAt(i) > '9') return false;
        return true;
    }
    static List<List<Row>> sections(List<Item> items, boolean stale, String note, int more, Integer version) {
        List<List<Row>> out = new ArrayList<>(); String reason = clean(note, 300);
        if (reason != null) out.add(List.of(Row.note(reason)));
        List<Row> section = new ArrayList<>();
        for (Item item : items) {
            if (item == null) continue;
            if (item.separator()) { if (!section.isEmpty()) { out.add(List.copyOf(section)); section.clear(); } continue; }
            String title = clean(item.title(), 100); if (title == null) continue;
            Kind kind = item.submenu() ? Kind.SUBMENU : Kind.ITEM;
            section.add(new Row(kind, item.id(), title, kind == Kind.ITEM ? clean(item.key(), 16) : null,
                    item.id() != null && !Boolean.FALSE.equals(item.enabled()) && !stale, mark(item.mark()), version));
        }
        if (!section.isEmpty()) out.add(List.copyOf(section));
        if (more > 0) out.add(List.of(Row.note(more + " more on the Mac")));
        if (out.isEmpty()) out.add(List.of(Row.note(NO_ITEMS)));
        return List.copyOf(out);
    }
    static Mark mark(String raw) {
        String text = clean(raw, 1); return text == null ? Mark.OFF : text.equals("-") || text.equals("–") ? Mark.MIXED : Mark.ON;
    }
    static String clean(String input, int limit) {
        if (input == null || limit <= 0) return null;
        StringBuilder out = new StringBuilder(); boolean space = false;
        for (int i = 0; i < input.length();) {
            int cp = input.codePointAt(i); i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (cp == 0x85 || Character.isWhitespace(cp) || Character.isSpaceChar(cp) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                space = out.length() > 0; continue;
            }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.PRIVATE_USE || type == Character.UNASSIGNED) continue;
            if (space) out.append(' '); space = false; out.appendCodePoint(cp);
        }
        Matcher matcher = GRAPHEME.matcher(out); int end = 0;
        for (int count = 0; count < limit && matcher.find(); count++) end = matcher.end();
        String value = out.substring(0, end).trim(); return value.isEmpty() ? null : value;
    }
}
