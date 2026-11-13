package io.github.n2ray.sall;

public final class ScrollMomentumTest {
    public static void main(String[] args) {
        ScrollMomentum coast = new ScrollMomentum();
        assert !coast.start(149, 0, 1);
        assert coast.start(600, -300, 1);
        assert coast.firstX() == 10 && coast.firstY() == -5;
        assert coast.advance(1d / 60, 1);
        double first = coast.stepX(1d / 60);
        assert first > 0 && first < 10 && coast.stepY(1d / 60) < 0;
        assert coast.advance(1d / 60, 1) && coast.stepX(1d / 60) < first;
        int ticks = 0;
        while (coast.advance(1d / 60, 1) && ticks++ < 1000) { }
        assert ticks < 1000;
        assert !coast.start(Double.NaN, 10, 1);
        assert coast.start(0, 600, 2);
        coast.clear();
        assert !coast.advance(1d / 60, 2);
        System.out.println("Scroll momentum checks passed");
    }
}
