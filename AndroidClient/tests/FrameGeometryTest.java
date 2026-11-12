package io.github.n2ray.sill;
public class FrameGeometryTest {
    public static void main(String[] args) {
        var wide = FrameGeometry.fit(160, 100, 160, 90);
        assert wide.left() == 0 && wide.top() == 5 && wide.width() == 160 && wide.height() == 90;
        assert wide.x(80) == .5f && wide.y(50) == .5f && wide.y(0) == 0 && wide.y(100) == 1;
        var tall = FrameGeometry.fit(160, 100, 100, 100);
        assert tall.left() == 30 && tall.top() == 0 && tall.width() == 100;
        assert tall.x(0) == 0 && tall.x(160) == 1 && tall.x(80) == .5f;
        try { FrameGeometry.fit(0, 100, 100, 100); throw new AssertionError("accepted zero size"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("Frame geometry checks passed");
    }
}
