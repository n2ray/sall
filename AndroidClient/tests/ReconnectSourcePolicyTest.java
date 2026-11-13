package io.github.n2ray.sall;

public final class ReconnectSourcePolicyTest {
    private static final InitialSourcePolicy.Source NONE = InitialSourcePolicy.Source.NONE;
    private static final InitialSourcePolicy.Source WINDOW = InitialSourcePolicy.Source.WINDOW;
    private static final InitialSourcePolicy.Source DESKTOP = InitialSourcePolicy.Source.DESKTOP;
    public static void main(String[] args) {
        ReconnectSourcePolicy policy = new ReconnectSourcePolicy();
        policy.onList(WINDOW, 42, "one", false, true);
        policy.disconnected();
        assert policy.pending();
        assert policy.onList(NONE, -1, "one", false, false) == null && policy.pending();
        assert policy.onList(NONE, -1, "one", true, true) == 42;
        assert policy.onList(NONE, -1, "one", true, true) == null;
        policy.onList(WINDOW, 42, "one", false, true);
        policy.disconnected();
        assert policy.onList(NONE, -1, "one", false, true) == -1;
        policy.disconnected();
        assert policy.onList(NONE, -1, "two", true, true) == -1;
        policy.onList(WINDOW, 42, "one", false, true);
        policy.disconnected();
        policy.onList(DESKTOP, -1, "one", false, true);
        assert !policy.pending();
        policy.disconnected();
        assert policy.onList(NONE, -1, "one", false, true) == -1;
        policy.onList(WINDOW, 42, "one", false, true);
        policy.disconnected();
        policy.chosen();
        assert !policy.pending();
        policy.disconnected();
        assert !policy.pending();
        System.out.println("ReconnectSourcePolicyTest passed");
    }
}
