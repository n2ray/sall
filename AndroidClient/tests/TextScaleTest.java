package io.github.n2ray.sill;
public final class TextScaleTest {
    public static void main(String[] args) {
        assert TextScale.offersOff(null, null);
        assert !TextScale.offersOff(true, null);
        assert TextScale.offersOff(true, false);
        assert !TextScale.offersOff(false, true);
        assert TextScale.position(null, true) == .25;
        assert TextScale.position(null, false) == 1;
        assert TextScale.snap(.1, false) == .5;
        assert TextScale.snap(2, true) == 1.5;
        assert TextScale.snap(.86, true) == .75;
        assert TextScale.scale(.25) == null;
        assert TextScale.singlePointer(1);
        assert !TextScale.singlePointer(2);
        assert TextScale.title(1).equals("1×");
        assert TextScale.title(.75).equals("0.75×");
        System.out.println("Text scale checks passed");
    }
}
