package io.github.n2ray.sall;

public final class DesktopNavigationTest {
    public static void main(String[] args) {
        assert DesktopNavigation.next(true, 3, 1);
        assert DesktopNavigation.next(true, 3, 3);
        assert !DesktopNavigation.next(false, 3, 1);
        assert !DesktopNavigation.next(true, 0, 0);
        assert !DesktopNavigation.next(true, 1, 1);
        assert !DesktopNavigation.next(true, 3, 4);
        assert DesktopNavigation.dots(true, 3, 2) == 3;
        assert DesktopNavigation.dots(false, 3, 2) == 0;
        assert DesktopNavigation.dots(true, 7, 2) == 0;
        assert "2/7".equals(DesktopNavigation.counter(true, 7, 2));
        assert DesktopNavigation.counter(true, 3, 4) == null;
        System.out.println("Desktop navigation checks passed");
    }
}
