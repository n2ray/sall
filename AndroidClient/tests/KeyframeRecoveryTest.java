package io.github.n2ray.sall;

public final class KeyframeRecoveryTest {
    public static void main(String[] args) {
        KeyframeRecovery r = new KeyframeRecovery();
        assert !r.consider(0, true, true, 0);
        r.interrupted(0);
        assert !r.consider(0, false, true, 0);
        assert !r.consider(0, true, false, 0);
        assert r.consider(0, true, true, 0);
        assert !r.consider(3999, true, true, 0);
        assert r.consider(4000, true, true, 0);
        assert !r.consider(11999, true, true, 0);
        assert r.consider(12000, true, true, 0);
        assert r.consider(28000, true, true, 0);
        assert r.consider(60000, true, true, 0);
        assert r.consider(92000, true, true, 0);
        long generation = r.generation();
        r.pictureBack(93000, true, generation - 1);
        assert r.isOpen();
        r.pictureBack(93000, true, generation);
        assert !r.isOpen();
        r.interrupted(94000);
        assert !r.consider(123999, true, true, 0);
        assert r.consider(124000, true, true, 0);
        r.leftForeground();
        assert !r.consider(126000, false, true, 0);
        assert r.consider(126000, true, true, 0);
        KeyframeRecovery slow = new KeyframeRecovery();
        slow.interrupted(0);
        assert slow.consider(0, true, true, 3000);
        assert !slow.consider(11999, true, true, 3000);
        assert slow.consider(12000, true, true, 3000);
        System.out.println("KeyframeRecoveryTest passed");
    }
}
