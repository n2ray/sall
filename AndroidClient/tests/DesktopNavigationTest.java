package io.github.n2ray.sall;

public final class DesktopNavigationTest {
    public static void main(String[] args) {
        assert DesktopNavigation.next(true, 3, 1);
        assert DesktopNavigation.next(true, 3, 3);
        assert !DesktopNavigation.next(false, 3, 1);
        assert !DesktopNavigation.next(true, 0, 0);
        assert !DesktopNavigation.next(true, 1, 1);
        assert !DesktopNavigation.next(true, 3, 4);
        System.out.println("Desktop navigation checks passed");
    }
}
