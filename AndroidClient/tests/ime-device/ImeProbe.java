package io.github.n2ray.sall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises the production RemoteKeyboard against the device framework without a Mac connection. */
public final class ImeProbe extends Instrumentation {
    private ImeProbeActivity activity;
    private RemoteKeyboard keyboard;
    private InputConnection connection;
    private final List<String> sent = new ArrayList<>();
    private void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    private void expect(String... values) {
        check(sent.equals(Arrays.asList(values)), "unexpected local listener events"); sent.clear();
    }
    private void begin() {
        connection = keyboard.onCreateInputConnection(new EditorInfo());
        check(connection.commitText("한글\nㅎ", 1), "initial commit"); expect("한글\nㅎ");
        check(connection.setSelection(2, 4), "selection");
        check(connection.commitText("", 1), "delete selection"); expect("\b");
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Intent intent = new Intent().setClassName("io.github.n2ray.sall.imeprobe", ImeProbeActivity.class.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (ImeProbeActivity) startActivitySync(intent);
            runOnMainSync(() -> {
                keyboard = new RemoteKeyboard(activity, new RemoteKeyboard.Listener() {
                    public void text(String text) { sent.add(text); }
                    public void key(int usage, boolean down) { }
                });
                activity.setContentView(keyboard);
                begin();
                check(connection.finishComposingText(), "no-op finish");
                check(connection.setComposingText("\n하", 1), "marked retype");
                check(connection.commitText("\n하", 1), "marked commit"); expect("하");
                check(!connection.commitText("\n", 1), "stale marked connection");
                connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.commitText("\n", 1), "new genuine return"); expect("\n");

                keyboard.discard(); begin();
                check(connection.finishComposingText(), "finish before direct retype");
                check(connection.commitText("\n", 1), "direct retyped return"); expect();
                check(connection.commitText("하", 1), "suffix after direct retype"); expect("하");
                check(connection.commitText("\n", 1), "genuine return after suffix"); expect("\n");

                keyboard.discard(); begin();
                check(connection.finishComposingText(), "no-op finish 2");
                check(connection.setComposingText("\n", 1), "marked return");
                check(connection.commitText("\n", 1), "commit retyped return"); expect();
                check(!connection.commitText("\n", 1), "stale return connection");
                connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.commitText("\n", 1), "new return after retype"); expect("\n");

                keyboard.discard(); begin();
                check(connection.setComposingText("하", 1), "marked suffix");
                check(connection.setComposingText("한", 1), "marked update");
                check(connection.commitText("\n한", 1), "commit full retype"); expect("한");

                keyboard.discard(); begin();
                check(connection.setComposingText("\n하", 1), "marked retype before finish");
                check(connection.finishComposingText(), "finish marked"); expect("하");
                check(!connection.commitText("\n하", 1), "late commit after finish");
                connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.commitText("\n", 1), "genuine return after finish"); expect("\n");

                // Dictation-style hypotheses remain local until the IME commits a candidate.
                keyboard.discard(); connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.setComposingText("我", 1), "dictation first hypothesis"); expect();
                check(connection.setComposingText("我想", 1), "dictation second hypothesis"); expect();
                check(connection.setComposingText("我想去", 1), "dictation final hypothesis"); expect();
                check(connection.commitText("我想去", 1), "dictation commit"); expect("我想去");

                // A recognizer may mark and revise a suffix it already committed.
                check(connection.setComposingRegion(1, 3), "dictation reopen committed suffix"); expect();
                check(connection.setComposingText("需要", 1), "dictation revise marked suffix"); expect();
                check(connection.commitText("需要", 1), "dictation commit revision"); expect("\b\b", "需要");
                check(connection.setSelection(1, 3), "dictation select committed suffix");
                check(connection.commitText("希望", 1), "dictation replace selection"); expect("\b\b", "希望");
                check(connection.commitText("\n", 1), "dictation newline"); expect("\n");
                check(connection.setComposingText("今天", 1), "dictation next line"); expect();
                check(connection.commitText("今天", 1), "dictation next line commit"); expect("今天");

                InputConnection discarded = connection;
                keyboard.discard();
                check(!discarded.setComposingText("旧", 1), "discarded composition rejected");
                check(!discarded.setComposingRegion(0, 1), "discarded region rejected");
                check(!discarded.commitText("旧", 1), "discarded commit rejected"); expect();
                connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.commitText("语音", 1), "new generation commit"); expect("语音");
                InputConnection finished = connection;
                keyboard.finishAndForget(); expect();
                check(!finished.setComposingText("旧", 1), "finished composition rejected");
                check(!finished.commitText("旧", 1), "finished commit rejected"); expect();

                keyboard.discard(); connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(connection.commitText("a".repeat(4001), 1), "long commit"); expect("a".repeat(4001));
            });
            waitForIdleSync();
            runOnMainSync(() -> {
                check(!connection.commitText("stale", 1), "old connection after trim");
                connection = keyboard.onCreateInputConnection(new EditorInfo());
                check(((BaseInputConnection) connection).getEditable().length() == 1000, "trimmed tail");
                check(connection.commitText("b", 1), "new connection after trim"); expect("b");
                activity.finish();
            });
            result.putString("result", "PASS: real Android InputConnection replay and trim");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            if (activity != null) runOnMainSync(() -> activity.finish());
            result.putString("failure", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
