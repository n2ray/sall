package io.github.n2ray.sill;

public final class ClipboardPastePolicyTest {
    public static void main(String[] args) {
        assert ClipboardPastePolicy.text(null, 0) == ClipboardPastePolicy.Text.EMPTY;
        assert ClipboardPastePolicy.text("", 0) == ClipboardPastePolicy.Text.EMPTY;
        assert ClipboardPastePolicy.text("a".repeat(262144), 262155) == ClipboardPastePolicy.Text.READY;
        assert ClipboardPastePolicy.text("a".repeat(262145), 262156) == ClipboardPastePolicy.Text.TOO_LONG;
        assert ClipboardPastePolicy.text("🙂".repeat(65536), 262155) == ClipboardPastePolicy.Text.READY;
        assert ClipboardPastePolicy.text("🙂".repeat(65537), 262159) == ClipboardPastePolicy.Text.TOO_LONG;
        assert ClipboardPastePolicy.text("a", 1048576) == ClipboardPastePolicy.Text.READY;
        assert ClipboardPastePolicy.text("a", 1048577) == ClipboardPastePolicy.Text.TOO_LONG;
        assert ClipboardPastePolicy.text("a", -1) == ClipboardPastePolicy.Text.TOO_LONG;
        for (int mask = 0; mask < 256; mask++) {
            boolean[] f = new boolean[8];
            for (int i = 0; i < f.length; i++) f[i] = (mask & (1 << i)) != 0;
            assert ClipboardPastePolicy.paste(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7]) == (mask == 255);
        }
        System.out.println("Clipboard paste policy checks passed");
    }
}
