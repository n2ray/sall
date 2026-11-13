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
    private final InputGeneration generation = new InputGeneration();
    private final java.util.Set<Integer> keysDown = new java.util.HashSet<>();
    private BaseInputConnection active;
    private boolean mutating;
    private boolean preserveOnCreate, trimPosted;
    private long activeGeneration;
    RemoteKeyboard(Context context, Listener listener) {
        super(context); this.listener = listener; setFocusableInTouchMode(true);
        setContentDescription("Mac 键盘输入");
    }
    @Override public boolean onCheckIsTextEditor() { return true; }
    boolean isComposing() {
        return active != null && BaseInputConnection.getComposingSpanStart(active.getEditable()) >= 0;
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo out) {
        releaseKeys();
        boolean preserve = preserveOnCreate;
        preserveOnCreate = false;
        if (!preserve) finishCurrent();
        final long thisGeneration = generation.next();
        activeGeneration = thisGeneration;
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_NONE;
        active = new BaseInputConnection(this, true) {
            private boolean valid() { return generation.current(thisGeneration); }
            @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
                if (!valid()) return false;
                Editable editable = getEditable();
                boolean marked = BaseInputConnection.getComposingSpanStart(editable) >= 0;
                if (!marked) {
                    int start = android.text.Selection.getSelectionStart(editable), end = android.text.Selection.getSelectionEnd(editable);
                    if (start >= 0 && end >= 0) deleteCrossingSelection(editable, Math.min(start, end), Math.max(start, end));
                }
                text = state.filterComposing(text.toString());
                if (text == null) return true;
                if (text.length() == 0 && !marked) return true;
                return super.setComposingText(text, newCursorPosition);
            }
            @Override public boolean setComposingRegion(int start, int end) {
                return valid() && super.setComposingRegion(start, end);
            }
            @Override public boolean setSelection(int start, int end) {
                if (!valid()) return false;
                Editable editable = getEditable();
                if (BaseInputConnection.getComposingSpanStart(editable) < 0 && start >= 0 && end >= 0
                        && start <= editable.length() && end <= editable.length() && editable.toString().equals(state.text())) {
                    int[] selection = state.selection(start, end);
                    start = selection[0]; end = selection[1];
                }
                if (start != end) {
                    state.clearRetype();
                    if (BaseInputConnection.getComposingSpanStart(editable) < 0) state.clearComposingRetype();
                }
                return super.setSelection(start, end);
            }
            @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                if (!valid()) return false;
                Editable editable = getEditable();
                boolean retiring = state.hasComposingRetype();
                if (retiring) {
                    String rest = state.filterComposingCommit(text.toString());
                    if (rest == null) { restartPreserving(); return true; }
                    text = rest;
                }
                if (BaseInputConnection.getComposingSpanStart(editable) < 0) {
                    int start = android.text.Selection.getSelectionStart(editable), end = android.text.Selection.getSelectionEnd(editable);
                    int left = Math.min(start, end), right = Math.max(start, end);
                    if (start >= 0 && end >= 0 && text.length() == 0 && deleteCrossingSelection(editable, left, right)) return true;
                    if (start == editable.length() && end == editable.length()) {
                        String rest = state.consumeRetype(text.toString());
                        if (rest == null || rest.isEmpty()) { if (retiring) restartPreserving(); return true; }
                        text = rest;
                    }
                }
                mutating = true;
                boolean result;
                try { result = super.commitText(text, newCursorPosition); } finally { mutating = false; }
                if (result) reconcile(getEditable());
                if (retiring) restartPreserving();
                return result;
            }
            @Override public boolean finishComposingText() {
                if (!valid()) return false;
                boolean retiring = !mutating && state.hasComposingRetype()
                        && BaseInputConnection.getComposingSpanStart(getEditable()) >= 0;
                boolean result = super.finishComposingText();
                if (result && !mutating) reconcile(getEditable());
                if (result && retiring) { state.clearComposingRetype(); restartPreserving(); }
                return result;
            }
            @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                if (!valid() || beforeLength < 0 || afterLength < 0) return false;
                if (BaseInputConnection.getComposingSpanStart(getEditable()) >= 0)
                    return super.deleteSurroundingText(beforeLength, afterLength);
                Editable editable = getEditable();
                int start = android.text.Selection.getSelectionStart(editable), end = android.text.Selection.getSelectionEnd(editable);
                if (start < 0 || end < 0) return false;
                int left = Math.min(start, end), right = Math.max(start, end);
                if (RemoteTextState.splitsSurrogate(editable, left) || RemoteTextState.splitsSurrogate(editable, right)) return false;
                int before = Math.min(beforeLength, 64), after = Math.min(afterLength, 64);
                int beyond = Math.max(0, before - left);
                if (after > editable.length() - right || (beyond > 0 && (before != 1 || left != right || right != editable.length() || after != 0))) return false;
                boolean finalBackspace = left == right && right == editable.length() && before == 1 && after == 0;
                if (before <= left) before = left - RemoteTextState.deletionStart(editable, left - before);
                after = RemoteTextState.deletionEnd(editable, right + after) - right;
                boolean result = super.deleteSurroundingText(before, after);
                if (result) {
                    if (reconcile(editable, finalBackspace) && beyond > 0) listener.text("\b".repeat(beyond));
                }
                return result;
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                if (!valid() || beforeLength < 0 || afterLength < 0) return false;
                Editable editable = getEditable();
                if (BaseInputConnection.getComposingSpanStart(editable) >= 0)
                    return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
                int selStart = android.text.Selection.getSelectionStart(editable), selEnd = android.text.Selection.getSelectionEnd(editable);
                if (selStart < 0 || selEnd < 0) return false;
                int left = Math.min(selStart, selEnd), right = Math.max(selStart, selEnd);
                if (RemoteTextState.splitsSurrogate(editable, left) || RemoteTextState.splitsSurrogate(editable, right)) return false;
                int before = Math.min(beforeLength, 64), after = Math.min(afterLength, 64);
                int known = Character.codePointCount(editable, 0, left);
                int knownAfter = Character.codePointCount(editable, right, editable.length());
                if (after > knownAfter || (before > known && (before != 1 || left != right || right != editable.length() || after != 0))) return false;
                int start = Character.offsetByCodePoints(editable, left, -Math.min(before, known));
                int end = Character.offsetByCodePoints(editable, right, after);
                boolean finalBackspace = left == right && right == editable.length() && before == 1 && after == 0;
                boolean result = super.deleteSurroundingText(left - start, end - right);
                if (result) {
                    if (reconcile(editable, finalBackspace) && before > known) listener.text("\b".repeat(before - known));
                }
                return result;
            }
            @Override public boolean sendKeyEvent(KeyEvent event) {
                if (!valid()) return false;
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
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    finishCurrent();
                    if (keysDown.add(usage)) listener.key(usage, true);
                } else if (event.getAction() == KeyEvent.ACTION_UP && keysDown.remove(usage)) {
                    listener.key(usage, false);
                    finishAndForget();
                }
                return true;
            }
        };
        if (preserve && !state.text().isEmpty()) {
            Editable editable = active.getEditable();
            editable.replace(0, editable.length(), state.text());
            android.text.Selection.setSelection(editable, editable.length());
        }
        return active;
    }

    private boolean deleteCrossingSelection(Editable editable, int start, int end) {
        if (!editable.toString().equals(state.text())) return false;
        RemoteTextState.Edit edit = state.deleteCrossingSelection(start, end);
        if (edit == null) return false;
        editable.replace(0, editable.length(), state.text());
        android.text.Selection.setSelection(editable, editable.length());
        if (edit.backspaces() > 0) listener.text("\b".repeat(edit.backspaces()));
        if (!edit.text().isEmpty()) listener.text(edit.text());
        return true;
    }

    private void reconcile(Editable editable) { reconcile(editable, false); }
    private boolean reconcile(Editable editable, boolean finalBackspace) {
        int previousFence = state.fence();
        RemoteTextState.Edit edit = state.reconcile(editable.toString(), finalBackspace);
        if (edit == null) {
            editable.replace(0, editable.length(), state.text());
            android.text.Selection.setSelection(editable, editable.length());
            return false;
        }
        if (edit.backspaces() > 0) listener.text("\b".repeat(edit.backspaces()));
        if (!edit.text().isEmpty()) listener.text(edit.text());
        if (finalBackspace && state.fence() < previousFence) {
            mutating = true;
            try { finishAndForget(); } finally { mutating = false; }
        } else scheduleTrim();
        return true;
    }

    private void scheduleTrim() {
        if (trimPosted || state.hasRetype() || state.hasComposingRetype() || !state.wantsTrim() || isComposing()) return;
        trimPosted = true;
        BaseInputConnection expected = active;
        long expectedGeneration = activeGeneration;
        post(() -> {
            trimPosted = false;
            if (active != expected || !generation.current(expectedGeneration) || isComposing() || state.hasRetype() || state.hasComposingRetype() || !state.trim()) return;
            Editable editable = expected.getEditable();
            editable.replace(0, editable.length(), state.text());
            android.text.Selection.setSelection(editable, editable.length());
            restartPreserving();
        });
    }

    private void restartPreserving() {
        generation.next();
        preserveOnCreate = true;
        if (hasFocus()) {
            android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.restartInput(this);
        }
    }

    /** Call before any action that can move the Mac's caret or select another window. */
    void finishAndForget() {
        preserveOnCreate = false;
        releaseKeys(); finishCurrent();
        generation.next();
        if (hasFocus()) {
            android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.restartInput(this);
        }
    }
    private void finishCurrent() {
        if (active != null) {
            active.finishComposingText();
            active.getEditable().clear();
        }
        state.clear();
    }
    private void releaseKeys() {
        for (int usage : keysDown) listener.key(usage, false);
        keysDown.clear();
    }
    void discard() {
        preserveOnCreate = false;
        releaseKeys();
        if (active != null) active.getEditable().clear();
        state.clear(); generation.next();
        if (hasFocus()) {
            android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.restartInput(this);
        }
    }
}
