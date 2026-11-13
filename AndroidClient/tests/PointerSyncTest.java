package io.github.n2ray.sall;

public final class PointerSyncTest {
    public static void main(String[] args) {
        PointerSync sync = new PointerSync();
        assert sync.accepts(0, true, .2, .8);
        sync.inputQueued();
        assert !sync.accepts(0, true, .2, .8);
        assert sync.accepts(1, true, .2, .8);
        sync.inputQueued();
        assert !sync.accepts(1, true, .2, .8);
        assert !sync.accepts(2, false, .2, .8);
        assert !sync.accepts(2, true, Double.NaN, .8);
        assert !sync.accepts(2, true, -.1, .8);
        assert !sync.accepts(2, true, .2, 1.1);
        assert sync.accepts(2, true, 0, 1);
        System.out.println("Pointer sync checks passed");
    }
}
