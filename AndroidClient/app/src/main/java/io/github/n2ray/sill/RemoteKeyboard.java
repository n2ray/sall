package io.github.n2ray.sill;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Composing candidates stay on the phone; committed suffixes can be corrected by the IME. */
final class RemoteKeyboard extends View {
    interface Listener { void text(String value); void key(int usage, boolean down); }
    private final Listener listener;
    private final RemoteTextState state = new RemoteTextState();
    private BaseInputConnection active;
    private boolean mutating;
    RemoteKeyboard(Context context, Listener listener) {
        super(context); this.listener = listener; setFocusableInTouchMode(true);
        setContentDescription("Mac 键盘输入");
    }
    @Override public boolean onCheckIsTextEditor() { return true; }
    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        finishAndForget();
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        active = new BaseInputConnection(this, true) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                mutating = true;
                boolean result;
                try { result = super.commitText(text, newCursorPosition); } finally { mutating = false; }
                if (result) reconcile(getEditable());
                return result;
            }
            @Override public boolean finishComposingText() {
                boolean result = super.finishComposingText();
                if (result && !mutating) reconcile(getEditable());
                return result;
            }
            @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (BaseInputConnection.getComposingSpanStart(getEditable()) >= 0)
                    return super.deleteSurroundingText(beforeLength, afterLength);
                Editable editable = getEditable();
                int cursor = Math.max(0, android.text.Selection.getSelectionStart(editable));
                int beyond = Math.max(0, Math.min(beforeLength, 64) - cursor);
                boolean result = super.deleteSurroundingText(Math.min(beforeLength, 64), 0);
                if (result) {
                    reconcile(editable);
                    if (beyond > 0) listener.text("\b".repeat(beyond));
                    for (int i = 0; i < Math.min(afterLength, 64); i++) { listener.key(76, true); listener.key(76, false); }
                }
                return result;
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                int usage = switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_ENTER -> 40;
                    case KeyEvent.KEYCODE_DEL -> 42;
                    case KeyEvent.KEYCODE_TAB -> 43;
                    case KeyEvent.KEYCODE_ESCAPE -> 41;
                    default -> 0;
                };
                if (usage == 0) return super.sendKeyEvent(event);
                if (usage == 42 && BaseInputConnection.getComposingSpanStart(getEditable()) >= 0) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) super.deleteSurroundingText(1, 0);
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) finishAndForget();
                listener.key(usage, event.getAction() == KeyEvent.ACTION_DOWN); return true;
            }
        };
        return active;
    }

    private void reconcile(Editable editable) {
        RemoteTextState.Edit edit = state.reconcile(editable.toString());
        if (edit.backspaces() > 0) listener.text("\b".repeat(edit.backspaces()));
        if (!edit.text().isEmpty()) listener.text(edit.text());
        if (editable.length() > 2048 || edit.text().indexOf('\n') >= 0 || edit.text().indexOf('\t') >= 0) {
            editable.clear(); state.clear();
        }
    }

    /** Call before any action that can move the Mac's caret or select another window. */
    void finishAndForget() {
        if (active != null) {
            active.finishComposingText();
            active.getEditable().clear();
        }
        state.clear();
    }
    void discard() {
        if (active != null) active.getEditable().clear();
        state.clear();
    }
}
