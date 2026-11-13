package android.view.inputmethod;
import android.text.Editable;
import android.text.SimpleEditable;
import android.text.Selection;
import android.view.View;
public class BaseInputConnection implements InputConnection {
    private final SimpleEditable editable = new SimpleEditable();
    public BaseInputConnection(View target, boolean fullEditor) { }
    public Editable getEditable() { return editable; }
    public static int getComposingSpanStart(Editable e) { return ((SimpleEditable)e).markedStart; }
    public boolean setComposingRegion(int start, int end) { editable.markedStart=start; editable.markedEnd=end; return true; }
    public boolean setSelection(int start, int end) { Selection.setSelection(editable, start, end); return true; }
    public boolean setComposingText(CharSequence text, int position) {
        int start = editable.markedStart >= 0 ? editable.markedStart : Math.min(editable.start, editable.end);
        int end = editable.markedStart >= 0 ? editable.markedEnd : Math.max(editable.start, editable.end);
        editable.replace(start, end, text);
        editable.markedStart = text.length() == 0 ? -1 : start;
        editable.markedEnd = text.length() == 0 ? -1 : start + text.length();
        int cursor = position > 0 ? start + text.length() + position - 1 : start + position;
        Selection.setSelection(editable, Math.max(0, Math.min(editable.length(), cursor)));
        return true;
    }
    public boolean commitText(CharSequence text, int position) {
        int start = editable.markedStart >= 0 ? editable.markedStart : Math.min(editable.start, editable.end);
        int end = editable.markedStart >= 0 ? editable.markedEnd : Math.max(editable.start, editable.end);
        editable.replace(start, end, text); editable.markedStart=editable.markedEnd=-1;
        int cursor = position > 0 ? start + text.length() + position - 1 : start + position;
        Selection.setSelection(editable, Math.max(0, Math.min(editable.length(), cursor)));
        return true;
    }
    public boolean finishComposingText() { editable.markedStart=editable.markedEnd=-1; return true; }
    public boolean deleteSurroundingText(int before, int after) {
        int left = Math.min(editable.start, editable.end), right = Math.max(editable.start, editable.end);
        if (left < 0 || right < 0) return false;
        if (editable.markedStart >= 0) { left = Math.min(left, editable.markedStart); right = Math.max(right, editable.markedEnd); }
        int oldStart=editable.start, oldEnd=editable.end;
        int from=Math.max(0, left-before), to=Math.min(editable.length(), right+after);
        int removed=left-from;
        if (to > right) editable.replace(right, to, "");
        if (removed > 0) editable.replace(from, left, "");
        Selection.setSelection(editable, Math.max(0,oldStart-removed), Math.max(0,oldEnd-removed));
        if (editable.markedStart >= 0) { editable.markedStart-=removed; editable.markedEnd-=removed; }
        return true;
    }
    public boolean deleteSurroundingTextInCodePoints(int before,int after) {
        int left=Math.min(editable.start,editable.end), right=Math.max(editable.start,editable.end);
        if (left < 0 || right < 0) return true;
        if (editable.markedStart >= 0) { left=Math.min(left,editable.markedStart); right=Math.max(right,editable.markedEnd); }
        int back=Math.min(before, Character.codePointCount(editable,0,left));
        int ahead=Math.min(after, Character.codePointCount(editable,right,editable.length()));
        return deleteSurroundingText(left-Character.offsetByCodePoints(editable,left,-back), Character.offsetByCodePoints(editable,right,ahead)-right);
    }
    public boolean sendKeyEvent(android.view.KeyEvent event) { return false; }
}
