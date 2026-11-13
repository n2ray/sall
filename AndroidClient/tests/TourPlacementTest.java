package io.github.n2ray.sall;

public final class TourPlacementTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(TourPlacement.width(800, 400, true, false, 1) == 480, "short landscape width");
        check(TourPlacement.width(800, 400, true, true, 1) == 560, "accessibility width");
        check(TourPlacement.width(350, 800, false, true, 1) == 318, "width within margins");
        var cut = TourPlacement.cutout(new TourPlacement.Rect(2, 12, 48, 28), 100, 100, 1);
        check(cut.equals(new TourPlacement.Rect(0, 8, 52, 32)), "outset clipped");
        var picture = new TourPlacement.Rect(0, 0, 400, 350);
        var target = new TourPlacement.Rect(0, 400, 400, 450);
        var p = TourPlacement.place(400, 800, 360, 200, false, false, picture, picture, target, 1);
        check(p.card().bottom() == 338 && p.tail() == 0 && !p.coversTargets(), "upright card rests in picture room");
        var below = TourPlacement.place(800, 400, 480, 200, true, false,
                new TourPlacement.Rect(0, 60, 800, 400), null, new TourPlacement.Rect(0, 0, 800, 50), 1);
        check(below.card().top() == 62 && below.tail() == -1 && !below.coversTargets(), "landscape sits below toolbar");
        var covered = TourPlacement.place(200, 200, 168, 180, false, false,
                new TourPlacement.Rect(0, 0, 200, 40), null, new TourPlacement.Rect(0, 50, 200, 170), 1);
        check(covered.coversTargets() && covered.tail() == 0, "hide highlight when card must cover target");
        System.out.println("Tour placement checks passed");
    }
}
