package io.github.n2ray.sill;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/** Receives committed IME text only; composed candidates remain on the phone. */
final class RemoteKeyboard extends View {
    interface Listener { void text(String value); void key(int usage, boolean down); }
    private final Listener listener;
    RemoteKeyboard(Context context, Listener listener) {
        super(context); this.listener = listener; setFocusableInTouchMode(true);
        setContentDescription("Mac 键盘输入");
    }
    @Override public boolean onCheckIsTextEditor() { return true; }
    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, true) {
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() > 0) listener.text(text.toString());
                boolean result = super.commitText("", newCursorPosition);
                getEditable().clear();
                return result;
            }
            @Override public boolean finishComposingText() {
                String text = getEditable().toString();
                boolean result = super.finishComposingText();
                if (!text.isEmpty()) listener.text(text);
                getEditable().clear();
                return result;
            }
            @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (BaseInputConnection.getComposingSpanStart(getEditable()) >= 0)
                    return super.deleteSurroundingText(beforeLength, afterLength);
                for (int i = 0; i < Math.min(beforeLength, 64); i++) listener.text("\b");
                return true;
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
                listener.key(usage, event.getAction() == KeyEvent.ACTION_DOWN); return true;
            }
        };
    }
}
