package io.github.n2ray.sall;

public final class PictureStrokeTest {
    public static void main(String[] args) {
        PictureStroke s = new PictureStroke(); s.begin();
        assert s.mayClick(); s.singleMove(12, 8); assert s.owner() == PictureStroke.Owner.MAC && !s.mayClick();
        s.second(false); s.pairMove(30, 0, 8, 12); assert s.owner() == PictureStroke.Owner.MAC;
        s.begin(); s.second(false); assert !s.mayClick(); s.pairMove(14, 2, 8, 12); assert s.owner() == PictureStroke.Owner.PICTURE;
        s.begin(); s.second(false); s.pairMove(9, 0, 8, 12); assert s.owner() == PictureStroke.Owner.UNDECIDED;
        s.third(); assert s.owner() == PictureStroke.Owner.GESTURE && !s.mayClick();
        s.begin(); s.second(true); assert s.owner() == PictureStroke.Owner.PICTURE;
        s.begin(); s.second(false); s.pairMove(2, 10, 8, 12); assert s.owner() == PictureStroke.Owner.MAC;
        System.out.println("Picture stroke ownership checks passed");
    }
}
