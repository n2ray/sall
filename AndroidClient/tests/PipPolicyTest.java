package io.github.n2ray.sall;

public final class PipPolicyTest {
    public static void main(String[] args) {
        assert PipPolicy.aspect(1, 10000).equals(new PipPolicy.Aspect(100, 239));
        assert PipPolicy.aspect(10000, 1).equals(new PipPolicy.Aspect(239, 100));
        assert PipPolicy.aspect(100, 239).equals(new PipPolicy.Aspect(100, 239));
        assert PipPolicy.aspect(239, 100).equals(new PipPolicy.Aspect(239, 100));
        assert PipPolicy.aspect(1920, 1080).equals(new PipPolicy.Aspect(1920, 1080));
        assert PipPolicy.aspect(Integer.MAX_VALUE, 1).equals(new PipPolicy.Aspect(239, 100));
        assert PipPolicy.qualifies(true, true, true, true, true, false);
        for (int missing = 0; missing < 5; missing++) {
            boolean[] flags = {true, true, true, true, true}; flags[missing] = false;
            assert !PipPolicy.qualifies(flags[0], flags[1], flags[2], flags[3], flags[4], false);
        }
        assert !PipPolicy.qualifies(true, true, true, true, true, true);
        for (int kind : new int[]{6, 7, 8, 9, 15, 17, 24, 25, 28, 32, 47}) {
            assert !PipPolicy.maySend(true, kind, false);
            assert PipPolicy.maySend(false, kind, false);
        }
        assert PipPolicy.maySend(true, 8, true); // release a held key/button when entering PiP
        assert PipPolicy.maySend(true, 32, true); // unsubscribe is safe
        assert PipPolicy.maySend(true, 10, false) && PipPolicy.maySend(true, 44, false);
        PipPolicy p = new PipPolicy();
        assert !p.active() && !p.watched(false) && p.watched(true);
        p.setPlaying(false, 0); assert !p.paused();
        p.started(); assert p.active() && p.watched(false);
        p.setPlaying(false, 100); assert p.paused() && !p.watched(false) && p.watched(true);
        p.setPlaying(false, 200);
        assert !p.pauseExpired(99) && !p.pauseExpired(600_099) && p.pauseExpired(600_100);
        p.setPlaying(true, 700_000); assert p.watched(false) && !p.pauseExpired(900_000);
        p.setPlaying(false, 800_000); assert !p.pauseExpired(1_399_999) && p.pauseExpired(1_400_000);
        p.ended(); assert !p.active() && !p.paused() && !p.pauseExpired(2_000_000);
        p.started(); assert p.watched(false) && !p.paused();
        System.out.println("Picture in picture policy checks passed");
    }
}
