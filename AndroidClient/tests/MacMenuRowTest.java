package io.github.n2ray.sill;

public final class MacMenuRowTest {
    public static void main(String[] args) {
        assert !MacMenuRow.selectable(true, true, "1", "Item");
        assert !MacMenuRow.selectable(false, false, "1", "Item");
        assert !MacMenuRow.selectable(false, true, "", "Item");
        assert MacMenuRow.selectable(false, true, "1", "Item");
        assert MacMenuRow.label("Item", "⌘A", null, false).equals("   Item   ⌘A");
        assert MacMenuRow.label("Item", "", "✓", false).startsWith("✓");
        assert MacMenuRow.label("Item", "", "–", false).startsWith("−");
        assert MacMenuRow.label("More", "", null, true).endsWith("›");
        assert MacMenuRow.noteText("Allow access (System Settings).").equals(new MacMenuRow.NoteText("Allow access.", "System Settings"));
        assert MacMenuRow.noteText("Not connected.").equals(new MacMenuRow.NoteText("Not connected.", null));
        assert MacMenuRow.noteText("Message ()").equals(new MacMenuRow.NoteText("Message ()", null));
        System.out.println("Mac menu row checks passed");
    }
}
