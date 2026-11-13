package io.github.n2ray.sill;

import android.content.Context;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.List;

public final class RemoteKeyboardConnectionTest {
    private static final class Sink implements RemoteKeyboard.Listener {
        final List<String> sent = new ArrayList<>();
        public void text(String value) { sent.add(value); }
        public void key(int usage, boolean down) { }
        void expect(String... expected) { assert sent.equals(List.of(expected)) : sent; sent.clear(); }
    }
    private static InputConnection begin(RemoteKeyboard keyboard, Sink sink) {
        InputConnection connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("한글\nㅎ", 1);
        sink.expect("한글\nㅎ");
        assert connection.setSelection(2, 4);
        assert connection.commitText("", 1);
        sink.expect("\b");
        return connection;
    }
    public static void main(String[] args) {
        Sink sink = new Sink();
        RemoteKeyboard keyboard = new RemoteKeyboard(new Context(), sink);
        InputConnection connection = begin(keyboard, sink);
        assert connection.finishComposingText();
        assert connection.setComposingText("\n하", 1);
        assert connection.commitText("\n하", 1);
        sink.expect("하");
        assert !connection.commitText("\n", 1); // stale after the composition fence retires
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("\n", 1);
        sink.expect("\n");

        keyboard.discard();
        connection = begin(keyboard, sink);
        assert connection.finishComposingText();
        assert connection.commitText("\n", 1);
        sink.expect();
        assert connection.commitText("하", 1);
        sink.expect("하");
        assert connection.commitText("\n", 1);
        sink.expect("\n");

        keyboard.discard();
        connection = begin(keyboard, sink);
        assert connection.finishComposingText();
        assert connection.setComposingText("\n", 1);
        assert connection.commitText("\n", 1);
        sink.expect();
        assert !connection.commitText("\n", 1);
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("\n", 1);
        sink.expect("\n");

        keyboard.discard();
        connection = begin(keyboard, sink);
        assert connection.setComposingText("하", 1);
        assert connection.setComposingText("한", 1);
        assert connection.commitText("\n한", 1);
        sink.expect("한");
        assert !connection.commitText("\n", 1);
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("\n", 1);
        sink.expect("\n");

        keyboard.discard();
        connection = begin(keyboard, sink);
        assert connection.setComposingText("\n하", 1);
        assert connection.finishComposingText();
        sink.expect("하");
        assert !connection.commitText("\n하", 1);
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("\n", 1);
        sink.expect("\n");

        keyboard.discard();
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert connection.commitText("a".repeat(4001), 1);
        sink.expect("a".repeat(4001));
        keyboard.runPosted();
        assert !connection.commitText("stale", 1);
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        assert ((BaseInputConnection) connection).getEditable().length() == 1000;
        assert connection.commitText("b", 1);
        sink.expect("b");
        System.out.println("RemoteKeyboard InputConnection replay and trim checks passed");
    }
}
