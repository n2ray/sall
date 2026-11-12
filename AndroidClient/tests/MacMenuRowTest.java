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
        System.out.println("Mac menu row checks passed");
    }
}
