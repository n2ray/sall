package io.github.n2ray.sall;

public final class MultiFingerGestureTest {
    private static MultiFingerGesture start(int fingers) {
        MultiFingerGesture g = new MultiFingerGesture();
        for (int i = 0; i < fingers; i++) {
            boolean silenced = g.down(i, i * 60, 0, i * 15, false);
            assert silenced == (i == 2);
        }
        return g;
    }
    private static MultiFingerGesture.Decision swipe(int fingers, int dx, int dy) {
        MultiFingerGesture g = start(fingers);
        for (int i = 0; i < fingers; i++) g.move(i, i * 60 + dx, dy, 150);
        return g.up(0, dx, dy, 160);
    }
    private static MultiFingerGesture.Decision pinch(double factor) {
        MultiFingerGesture g = start(3);
        for (int i = 0; i < 3; i++) g.move(i, 60 + (i - 1) * 60 * factor, 0, 150);
        return g.up(0, 60 - 60 * factor, 0, 160);
    }
    public static void main(String[] args) {
        assert swipe(3, 0, -60).equals(new MultiFingerGesture.Decision("swipeUp", 3));
        assert swipe(3, 0, 60).equals(new MultiFingerGesture.Decision("swipeDown", 3));
        assert swipe(3, -60, 0).equals(new MultiFingerGesture.Decision("swipeLeft", 3));
        assert swipe(4, 60, 0).equals(new MultiFingerGesture.Decision("swipeRight", 4));
        assert pinch(.5).equals(new MultiFingerGesture.Decision("pinch", 3));
        assert pinch(1.5).equals(new MultiFingerGesture.Decision("spread", 3));
        assert swipe(3, 60, 60) == null;
        MultiFingerGesture resting = start(4);
        for (int i = 0; i < 3; i++) resting.move(i, i * 60, -60, 150);
        assert resting.up(0, 0, -60, 160).equals(new MultiFingerGesture.Decision("swipeUp", 3));
        MultiFingerGesture flick = start(3);
        for (int i = 0; i < 3; i++) flick.move(i, i * 60, 25, 55);
        assert flick.up(0, 0, 25, 60).gesture().equals("swipeDown");
        MultiFingerGesture slowMove = start(3);
        for (int i = 0; i < 3; i++) slowMove.move(i, i * 60, 25, 1000);
        assert slowMove.up(0, 0, 25, 1010) == null;
        MultiFingerGesture slow = new MultiFingerGesture();
        slow.down(0, 0, 0, 0, false); slow.down(1, 60, 0, 100, false);
        assert slow.down(2, 120, 0, 300, false);
        for (int i = 0; i < 3; i++) slow.move(i, i * 60 + 80, 0, 500);
        assert slow.up(0, 80, 0, 510) == null;
        MultiFingerGesture late = new MultiFingerGesture();
        late.down(0, 0, 0, 0, false); late.down(1, 60, 0, 10, false); late.move(0, 30, 0, 20);
        assert !late.down(2, 120, 0, 30, false);
        assert late.up(0, 90, 0, 100) == null;
        MultiFingerGesture five = start(4); five.down(4, 240, 0, 60, false);
        for (int i = 0; i < 5; i++) five.move(i, i * 60 + 70, 0, 150);
        assert five.up(0, 70, 0, 160) == null;
        MultiFingerGesture cancelled = start(3); cancelled.cancel();
        assert cancelled.up(0, 60, 0, 150) == null;
        MultiFingerGesture one = start(3);
        for (int i = 0; i < 3; i++) one.move(i, i * 60 + 60, 0, 150);
        assert one.up(0, 60, 0, 160) != null;
        assert one.up(1, 120, 0, 170) == null;
        System.out.println("Multi-finger gesture checks passed");
    }
}
