package android.text;
public final class Selection {
    public static int getSelectionStart(Editable e) { return ((SimpleEditable)e).start; }
    public static int getSelectionEnd(Editable e) { return ((SimpleEditable)e).end; }
    public static void setSelection(Editable e, int where) { setSelection(e, where, where); }
    public static void setSelection(Editable e, int start, int end) { SimpleEditable s = (SimpleEditable)e; s.start = start; s.end = end; }
}
