package io.github.n2ray.sill;
import java.util.List;
import java.util.Map;
public final class ConnectionGroupsTest {
    public static void main(String[] args) {
        assert ConnectionGroups.clean(null) == null;
        assert ConnectionGroups.clean(" \t home \n").equals("Home");
        assert ConnectionGroups.clean("  工作  室 ").equals("工作 室");
        assert ConnectionGroups.clean("OTHER") == null;
        assert ConnectionGroups.clean("a".repeat(40)).equals("a".repeat(32));
        assert ConnectionGroups.clean("a".repeat(31) + "e\u0301" + "z").equals("a".repeat(31) + "e\u0301");
        assert ConnectionGroups.clean("a".repeat(31) + "👍🏽" + "z").equals("a".repeat(31) + "👍🏽");
        assert ConnectionGroups.clean("a".repeat(31) + "👨‍👩‍👧‍👦" + "z").equals("a".repeat(31) + "👨‍👩‍👧‍👦");
        assert ConnectionGroups.clean("a".repeat(31) + "🇩🇪" + "z").equals("a".repeat(31) + "🇩🇪");
        assert ConnectionGroups.clean("bad\u0001name") == null;
        assert ConnectionGroups.canonical("MY ROOM", List.of("My Room")).equals("My Room");
        assert ConnectionGroups.ordered(List.of("Z", "Work", "a"), false).equals(List.of("Work", "a", "Z"));
        assert ConnectionGroups.ordered(List.of(), true).equals(List.of("Home", "Work", "Personal"));
        Map<String, String> groups = Map.of("full-pin-a", "work", "full-pin-b", "WORK");
        var sections = ConnectionGroups.sections(List.of("full-pin-a", "full-pin-b", "ungrouped"), groups::get);
        assert List.copyOf(sections.keySet()).equals(List.of("Work", "Other"));
        assert sections.get("Work").equals(List.of("full-pin-a", "full-pin-b"));
        assert ConnectionGroups.sections(List.of("legacy"), ignored -> null).containsKey("");
        assert !ConnectionGroups.canEdit(false, false); // forgotten while dialog was open
        assert !ConnectionGroups.canEdit(true, true);
        assert ConnectionGroups.canEdit(false, true);
        System.out.println("Connection group rules passed");
    }
}
