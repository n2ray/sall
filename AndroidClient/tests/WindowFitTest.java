package io.github.n2ray.sill;

public final class WindowFitTest {
    public static void main(String[] args) {
        assert WindowFit.next(.6, 368, 225, 614, 374, 614, 374) == .6;
        double wider = WindowFit.next(.6, 368, 225, 914, 374, 914, 374);
        assert wider > .38 && wider < .41;
        double taller = WindowFit.next(.6, 368, 225, 614, 800, 614, 800);
        assert taller >= .25 && taller < .31;
        assert WindowFit.next(.6, 368, 225, 2116, 374, 2116, 374) == .25;
        assert WindowFit.next(.25, 368, 225, 2116, 900, 2116, 900) == .25;
        assert WindowFit.next(.6, 0, 225, 100, 100, 100, 100) == .6;
        assert WindowFit.next(.6, 368, 225, 164, 176, 164, 176) == .6;
        assert WindowFit.next(.6, 368, 225, 914, 374, 600, 375) == .6;
        assert WindowFit.next(.6, 368, 225, 2116, 502, 2116, 502) == .6;
        System.out.println("Window fit checks passed");
    }
}
