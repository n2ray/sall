package io.github.n2ray.sill;

public final class InitialSourcePolicyTest {
    public static void main(String[] args) {
        InitialSourcePolicy empty = new InitialSourcePolicy();
        assert !empty.listed();
        assert empty.onList(InitialSourcePolicy.Source.NONE, true);
        assert empty.listed();
        assert !empty.onList(InitialSourcePolicy.Source.NONE, true);
        for (InitialSourcePolicy.Source source : new InitialSourcePolicy.Source[] {
                InitialSourcePolicy.Source.DESKTOP, InitialSourcePolicy.Source.WINDOW, InitialSourcePolicy.Source.UNKNOWN }) {
            InitialSourcePolicy policy = new InitialSourcePolicy();
            assert !policy.onList(source, true);
            assert !policy.onList(InitialSourcePolicy.Source.NONE, true);
        }
        InitialSourcePolicy blocked = new InitialSourcePolicy();
        assert !blocked.onList(InitialSourcePolicy.Source.NONE, false);
        assert !blocked.onList(InitialSourcePolicy.Source.NONE, false);
        assert blocked.onList(InitialSourcePolicy.Source.NONE, true);
        assert !blocked.onList(InitialSourcePolicy.Source.NONE, true);
        InitialSourcePolicy chosen = new InitialSourcePolicy();
        assert !chosen.onList(InitialSourcePolicy.Source.NONE, false);
        chosen.chosen();
        assert !chosen.onList(InitialSourcePolicy.Source.NONE, true);
        InitialSourcePolicy changed = new InitialSourcePolicy();
        assert !changed.onList(InitialSourcePolicy.Source.NONE, false);
        assert !changed.onList(InitialSourcePolicy.Source.WINDOW, true);
        assert !changed.onList(InitialSourcePolicy.Source.NONE, true);
        System.out.println("InitialSourcePolicyTest passed");
    }
}
