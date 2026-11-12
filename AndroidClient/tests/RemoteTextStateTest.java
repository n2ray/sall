package io.github.n2ray.sill;

public final class RemoteTextStateTest {
    public static void main(String[] args) {
        RemoteTextState state = new RemoteTextState();
        assert state.reconcile("你").equals(new RemoteTextState.Edit(0, "你"));
        assert state.reconcile("你好").equals(new RemoteTextState.Edit(0, "好"));
        assert state.reconcile("你号").equals(new RemoteTextState.Edit(1, "号"));
        assert state.reconcile("你").equals(new RemoteTextState.Edit(1, ""));
        state.clear();
        assert state.reconcile("cafe\u0301").equals(new RemoteTextState.Edit(0, "cafe\u0301"));
        assert state.reconcile("cafe").equals(new RemoteTextState.Edit(1, "e"));
        state.clear();
        assert state.reconcile("A\ud83d\ude00").equals(new RemoteTextState.Edit(0, "A\ud83d\ude00"));
        assert state.reconcile("A").equals(new RemoteTextState.Edit(1, ""));
        String emoji = "A\ud83d\ude00B";
        assert RemoteTextState.splitsSurrogate(emoji, 2);
        assert !RemoteTextState.splitsSurrogate(emoji, 1);
        assert RemoteTextState.deletionStart(emoji, 2) == 1;
        assert RemoteTextState.deletionEnd(emoji, 2) == 3;
        assert RemoteTextState.deletionStart(emoji, 3) == 3;
        assert RemoteTextState.deletionEnd(emoji, 1) == 1;
        state.clear();
        assert state.reconcile("hello\nworld").equals(new RemoteTextState.Edit(0, "hello\nworld"));
        assert state.reconcile("hello\nword").equals(new RemoteTextState.Edit(2, "d"));
        assert state.reconcile("changed\nword") == null;
        assert state.text().equals("hello\nword");
        assert state.reconcile("hello\n").equals(new RemoteTextState.Edit(4, ""));
        assert state.reconcile("hello") == null;
        assert state.reconcile("hello", true).equals(new RemoteTextState.Edit(1, ""));
        assert state.reconcile("hell") == null;
        assert state.reconcile("hell", true).equals(new RemoteTextState.Edit(1, ""));
        assert state.reconcile("he", true) == null;
        assert state.reconcile("hel", true).equals(new RemoteTextState.Edit(1, ""));
        state.clear();
        assert state.reconcile("abcde").equals(new RemoteTextState.Edit(0, "abcde"));
        assert state.reconcile("abe").equals(new RemoteTextState.Edit(3, "e"));
        assert state.reconcile("ab\tcd").equals(new RemoteTextState.Edit(1, "\tcd"));
        assert state.reconcile("ab\tce").equals(new RemoteTextState.Edit(1, "e"));
        assert state.reconcile("ab\t").equals(new RemoteTextState.Edit(2, ""));
        assert state.reconcile("ab", true).equals(new RemoteTextState.Edit(1, ""));
        state.clear();
        assert state.reconcile("cmd\r").equals(new RemoteTextState.Edit(0, "cmd\r"));
        assert state.reconcile("cmd\r\n").equals(new RemoteTextState.Edit(0, "\n"));
        assert state.reconcile("cmd\r\nx").equals(new RemoteTextState.Edit(0, "x"));
        assert state.reconcile("cmd\r\ny").equals(new RemoteTextState.Edit(1, "y"));
        assert state.reconcile("cmd\r") == null;
        state.clear();
        assert state.reconcile("cmd\r\n").equals(new RemoteTextState.Edit(0, "cmd\r\n"));
        assert state.reconcile("cmd\r\nx").equals(new RemoteTextState.Edit(0, "x"));
        assert state.reconcile("cmd\r\ny").equals(new RemoteTextState.Edit(1, "y"));
        System.out.println("Remote text checks passed");
    }
}
