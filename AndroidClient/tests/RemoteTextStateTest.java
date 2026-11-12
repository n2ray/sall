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
        System.out.println("Remote text checks passed");
    }
}
