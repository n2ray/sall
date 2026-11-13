package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.List;

public final class MacMenuStateTest {
    private static MacMenuState.Item item(String id, String title, boolean separator, Boolean enabled, String mark, String key, boolean submenu) {
        return new MacMenuState.Item(id, title, separator, enabled, mark, key, submenu);
    }
    private static MacMenuState.Message message(Integer version, List<MacMenuState.Item> menus, Long answer,
                                                List<MacMenuState.Item> items, Integer more, Boolean pressed, boolean stale, String note) {
        return new MacMenuState.Message(version, "App", menus, answer, items, more, pressed, stale, note);
    }
    public static void main(String[] args) {
        var state = new MacMenuState();
        assert state.fetch("1", "File", 1, 1, 100, 0).content().message().equals(MacMenuState.NOT_CONNECTED);
        var top = List.of(item("0", "Apple", false, true, null, null, true), item("1", "File", false, null, null, null, true),
                item("1", "Duplicate", false, true, null, null, true), item("02", "Bad", false, true, null, null, true),
                item("2", "Edit", false, false, null, null, true));
        assert !state.receive(message(null, top, null, null, null, null, false, null)).topChanged();
        assert state.receive(message(3, top, null, null, null, null, false, null)).topChanged();
        assert state.top().menus().size() == 2 && !state.top().menus().get(1).enabled();
        assert !state.receive(message(3, top, null, null, null, null, false, null)).topChanged();
        assert state.barMenuId("9", "File").equals("1") && state.barMenuId("1", "No such menu") == null;
        assert state.fetch("1", "File", 2, 1, 10, 0).content().message().equals(MacMenuState.CHANGED);
        assert state.fetch(null, "File", 3, 1, 10, 0).kind() == MacMenuState.FetchKind.SETTLED;
        assert state.fetch("1", "File", 3, 1, 10, 0).kind() == MacMenuState.FetchKind.SEND;
        assert state.fetch("1", "File", 3, 2, 11, 10).kind() == MacMenuState.FetchKind.JOINED;
        assert state.oldestWait() == 0;
        assert state.receive(message(3, null, 11L, null, 0, null, false, null)).done().isEmpty();
        var separator = item(null, null, true, null, null, null, false);
        var open = item("1.2", "Open\nFile", false, true, "✓", "⌘O", false);
        var disabled = item("1.3", "Disabled", false, false, "–", null, true);
        var reply = state.receive(message(3, null, 10L, List.of(separator, open, separator, separator, disabled, separator), 2, null, true, "  stale  "));
        assert reply.done().size() == 2 && reply.done().get(0).content().equals(reply.done().get(1).content());
        var sections = reply.done().get(0).content().sections();
        assert sections.size() == 4 && sections.get(0).get(0).title().equals("stale");
        assert sections.get(1).get(0).title().equals("Open File") && !sections.get(1).get(0).enabled();
        assert sections.get(1).get(0).mark() == MacMenuState.Mark.ON && sections.get(1).get(0).key().equals("⌘O");
        assert sections.get(2).get(0).mark() == MacMenuState.Mark.MIXED && sections.get(2).get(0).key() == null;
        assert sections.get(3).get(0).title().equals("2 more on the Mac");
        assert state.receive(message(3, null, 10L, null, 0, null, false, null)).done().isEmpty();
        assert MacMenuState.sections(List.of(separator), false, null, 0, 3).get(0).get(0).title().equals(MacMenuState.NO_ITEMS);
        assert MacMenuState.clean("  a\n b\u202e c ", 100).equals("a b c");
        assert MacMenuState.clean("a\u0085b\u00a0c", 100).equals("a b c");
        assert MacMenuState.clean("e\u0301x", 1).equals("e\u0301");
        assert MacMenuState.mark("–") == MacMenuState.Mark.MIXED && MacMenuState.mark(" ") == MacMenuState.Mark.OFF;
        assert !MacMenuState.isTopId("０") && !MacMenuState.isTopId("12345");
        var many = new ArrayList<MacMenuState.Item>(); for (int i = 1; i < 40; i++) many.add(item("" + i, "x", false, true, null, null, false));
        assert MacMenuState.topRows(many, 3).size() == 32;
        assert state.fetch("1", "File", 3, 3, 12, 100).kind() == MacMenuState.FetchKind.SEND;
        assert state.expire(4099, 4000, "Mac").isEmpty();
        assert state.expire(4100, 4000, "Mac").get(0).content().message().contains("didn’t answer");
        assert state.receive(message(3, null, 12L, List.of(open), 0, null, false, null)).done().isEmpty();
        assert state.fetch("1", "File", 3, 4, 13, 0).kind() == MacMenuState.FetchKind.SEND;
        assert state.receive(message(4, top, null, null, null, null, false, null)).done().get(0).content().message().equals(MacMenuState.CHANGED);
        assert state.fetch("1", "File", 4, 5, 14, 0).kind() == MacMenuState.FetchKind.SEND;
        assert state.receive(message(5, null, 14L, List.of(open), 0, null, false, null)).done().get(0).content().message().equals(MacMenuState.CHANGED);
        var row = MacMenuState.sections(List.of(open), false, null, 0, 4).get(0).get(0);
        assert state.press(row, 99).kind() == MacMenuState.PressKind.SEND;
        assert state.receive(message(4, null, 99L, null, 0, false, false, "Rejected")).refusal().byMac();
        for (int i = 0; i < 17; i++) assert state.press(row, 200 + i).kind() == MacMenuState.PressKind.SEND;
        assert state.receive(message(4, null, 200L, null, 0, false, false, "late")).refusal() == null;
        assert state.receive(message(4, null, 216L, null, 0, false, false, "denied")).refusal().note().equals("denied");
        state.receive(message(4, top, null, null, null, null, true, null));
        assert state.press(row, 100).kind() == MacMenuState.PressKind.REFUSED;
        assert state.fetch("1", "File", 4, 6, 15, 0).kind() == MacMenuState.FetchKind.SETTLED;
        state.receive(message(4, top, null, null, null, null, false, null));
        state.fetch("1", "File", 4, 7, 16, 0);
        assert state.connectionReplaced().get(0).content().message().equals(MacMenuState.CHANGED) && state.hasMenus();
        state.fetch("1", "File", 4, 8, 17, 0);
        assert state.reset().get(0).content().message().equals(MacMenuState.NOT_CONNECTED) && !state.hasMenus();
        System.out.println("Mac menu state checks passed");
    }
}
