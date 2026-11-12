package io.github.n2ray.sill;

public class PictureZoomTest {
    public static void main(String[] args) {
        PictureZoom zoom = new PictureZoom();
        var initial = zoom.frame(200, 100, 200, 100);
        assert initial.width() == 200 && initial.left() == 0;
        zoom.change(2, 100, 50, 0, 0, 200, 100, 200, 100);
        var enlarged = zoom.frame(200, 100, 200, 100);
        assert enlarged.width() == 400 && enlarged.left() == -100 && enlarged.x(100) == .5f;
        zoom.change(1, 100, 50, 1000, 1000, 200, 100, 200, 100);
        var clamped = zoom.frame(200, 100, 200, 100);
        assert clamped.left() == 0 && clamped.top() == 0;
        zoom.change(.01f, 100, 50, 0, 0, 200, 100, 200, 100);
        assert zoom.frame(200, 100, 200, 100).left() == 0;
        zoom.change(100, 100, 50, 0, 0, 200, 100, 200, 100);
        assert zoom.frame(200, 100, 200, 100).width() == 800;
        zoom.reset();
        assert zoom.frame(200, 100, 200, 100).width() == 200;
        System.out.println("Picture zoom checks passed");
    }
}
