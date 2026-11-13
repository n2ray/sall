package io.github.n2ray.sill;

public final class ClipboardChipStateTest {
    public static void main(String[] args) {
        ClipboardChipState s = new ClipboardChipState();
        s.newSession(1, 0); s.context(true, false, false, 0);
        s.observe(1, 10, true, true, 0);
        assert s.visible() && s.nextWake(0) == 20_000;
        s.context(false, false, false, 7_000); s.advance(50_000);
        assert !s.visible() && s.nextWake(50_000) == Long.MAX_VALUE;
        s.context(true, true, false, 50_000); s.advance(60_000);
        s.context(true, false, true, 60_000); s.advance(70_000);
        assert s.visible() && s.nextWake(70_000) == Long.MAX_VALUE;
        s.context(true, false, false, 70_000);
        assert s.nextWake(70_000) == 83_000;
        s.note(1, ClipboardChipState.Note.SENT, 75_000);
        assert !s.visible() && s.visibleNote() == ClipboardChipState.Note.SENT && s.nextWake(75_000) == 78_000;
        s.advance(78_000); assert s.visible() && s.nextWake(78_000) == 86_000;
        s.advance(85_999); assert s.visible();
        s.advance(86_000); assert !s.visible() && s.handled() == 10;
        s.observe(1, 10, true, true, 86_000); assert !s.visible();
        s.observe(1, 11, true, true, 86_001); assert s.visible();
        s.dismiss(1, 86_002); assert !s.visible() && s.handled() == 11;

        s.observe(1, 12, true, true, 86_003);
        assert s.beginReceipt(1, 100, 12, 86_004) && !s.visible();
        s.observe(1, 13, true, true, 86_005);
        assert s.receipt(1, 99, 86_006) == ClipboardChipState.Receipt.STALE;
        assert s.receiptLate(1, 100, 86_007) && s.visible() && s.offered() == 13;
        assert s.receipt(1, 100, 86_008) == ClipboardChipState.Receipt.LATE;
        assert s.visible() && s.offered() == 13 && s.handled() == 11;
        assert s.beginReceipt(1, 101, 13, 86_009);
        assert s.receipt(1, 101, 86_010) == ClipboardChipState.Receipt.TIMELY;
        assert !s.visible() && s.handled() == 13;

        s.observe(1, 14, true, true, 86_011);
        assert s.beginReceipt(1, 102, 14, 86_012);
        s.newSession(2, 86_013); s.observe(2, 15, true, true, 86_014);
        assert s.receipt(1, 102, 86_015) == ClipboardChipState.Receipt.STALE;
        assert s.visible() && s.offered() == 15;
        s.observe(2, 15, true, false, 86_016); assert !s.visible();
        s.observe(2, 15, false, true, 86_017); assert !s.visible();
        s.observe(2, 16, true, true, 86_018); assert s.visible();
        s.advance(80_000); assert s.nextWake(80_000) == 106_018; // stale time cannot increase the budget

        ClipboardChipState restored = new ClipboardChipState();
        assert restored.restoreHandled(40L);
        assert !restored.restoreHandled(41L);
        restored.newSession(8, 0); restored.context(true, false, false, 0);
        restored.observe(8, 40, true, true, 0); assert !restored.visible();
        assert !restored.restoreHandled(41L);
        restored.observe(8, 41, true, true, 1); assert restored.visible();
        assert restored.beginReceipt(8, 201, 41, 2);
        restored.observe(8, 42, true, true, 3);
        assert !restored.markHandled(8, 41, 4); // stale Mac write cannot consume newer clipboard content
        assert restored.markHandled(8, 42, 4); // mark a new Mac-origin change while old receipt waits
        assert restored.receipt(8, 201, 5) == ClipboardChipState.Receipt.TIMELY;
        assert restored.handled() == 42 && !restored.visible();
        assert !restored.noteForReceipt(8, 201, ClipboardChipState.Note.SENT, 6);
        restored.observe(8, 43, true, true, 7); assert restored.visible();
        assert !restored.markHandled(7, 43, 100_000); // stale session must not advance the active clock
        assert restored.nextWake(7) == 20_007;

        assert restored.beginReceipt(8, 202, 43, 8);
        assert !restored.receiptFailed(8, 201, 9);
        assert restored.receiptFailed(8, 202, 9) && restored.visible();
        assert restored.noteForReceipt(8, 202, ClipboardChipState.Note.FAILED, 10);
        assert !restored.visible() && restored.visibleNote() == ClipboardChipState.Note.FAILED;
        assert restored.beginReceipt(8, 203, 43, 11); // new request clears the prior result
        assert restored.visibleNote() == null;
        assert !restored.noteForReceipt(8, 202, ClipboardChipState.Note.SENT, 12);
        assert restored.receipt(8, 203, 13) == ClipboardChipState.Receipt.TIMELY;
        assert restored.noteForReceipt(8, 203, ClipboardChipState.Note.PASTE_REQUESTED, 14);
        restored.observe(8, 44, true, true, 15); // a result for the previous copy cannot cover a new copy
        assert restored.visible() && restored.offered() == 44 && restored.visibleNote() == null;
        assert !restored.noteForReceipt(8, 203, ClipboardChipState.Note.SENT, 16);
        assert restored.markHandled(8, 44, 17) && !restored.visible() && restored.handled() == 44;
        System.out.println("Clipboard chip metadata, clock, receipt and session checks passed");
    }
}
