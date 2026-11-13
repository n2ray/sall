package io.github.n2ray.sall;

public class WindowAutoFitTest {
    private static void close(Double actual, double expected) {
        assert actual != null && Math.abs(actual - expected) < 1e-9 : actual;
    }

    public static void main(String[] args) {
        close(WindowAutoFit.next(368, 230, 920, 230, 1), .4);
        close(WindowAutoFit.next(368, 230, 920, 460, .5), .4);
        close(WindowAutoFit.next(368, 230, 368, 575, 1), .4);
        close(WindowAutoFit.next(368, 230, 1840, 230, 1), .25);
        close(WindowAutoFit.next(368, 230, 496, 490, 1), 230.0 / 490);
        close(WindowAutoFit.next(368, 230, 920, 300, 1), .4);
        assert WindowAutoFit.next(368, 230, 368, 230, 1) == null;
        assert WindowAutoFit.next(368, 230, 736, 460, 1) == null;
        assert WindowAutoFit.next(368, 230, 496, 490, 230.0 / 490) == null;
        assert WindowAutoFit.next(368, 230, 920, 150, 1) == null;
        assert WindowAutoFit.next(368, 230, 920, 575, .4) == null;
        assert WindowAutoFit.next(368, 230, 920, 230, .25) == null;
        assert WindowAutoFit.next(368, 230, 375, 230, 1) == null;
        assert WindowAutoFit.next(368, 230, 0, 230, 1) == null;
        assert WindowAutoFit.next(Double.NaN, 230, 920, 230, 1) == null;
        assert WindowAutoFit.next(368, Double.POSITIVE_INFINITY, 920, 230, 1) == null;
        assert WindowAutoFit.next(368, 230, 920, 230, .24) == null;
        assert WindowAutoFit.next(368, 230, 920, 230, Double.NaN) == null;
        assert WindowAutoFit.next(16385, 230, 920, 230, 1) == null;
        assert WindowAutoFit.next(368, 230, 65537, 230, 1) == null;
        assert WindowAutoFit.matchingFrame(920, 230, 1840, 460);
        assert !WindowAutoFit.matchingFrame(920, 230, 920, 460);
        assert !WindowAutoFit.matchingFrame(920, 230, 0, 460);
        System.out.println("Window auto fit estimates passed");
    }
}
