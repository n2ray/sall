package android.text;
public final class SimpleEditable implements Editable {
    private final StringBuilder value = new StringBuilder();
    public int start, end, markedStart = -1, markedEnd = -1;
    public int length() { return value.length(); }
    public char charAt(int index) { return value.charAt(index); }
    public CharSequence subSequence(int start, int end) { return value.subSequence(start, end); }
    public String toString() { return value.toString(); }
    public Editable replace(int from, int to, CharSequence text) { value.replace(from, to, text.toString()); start = end = from + text.length(); return this; }
    public void clear() { value.setLength(0); start = end = 0; markedStart = markedEnd = -1; }
}
