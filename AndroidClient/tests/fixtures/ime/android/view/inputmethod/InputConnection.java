package android.view.inputmethod;
public interface InputConnection {
    boolean setComposingText(CharSequence text, int position);
    boolean setComposingRegion(int start, int end);
    boolean setSelection(int start, int end);
    boolean commitText(CharSequence text, int position);
    boolean finishComposingText();
    boolean deleteSurroundingText(int before, int after);
    boolean deleteSurroundingTextInCodePoints(int before, int after);
    boolean sendKeyEvent(android.view.KeyEvent event);
}
