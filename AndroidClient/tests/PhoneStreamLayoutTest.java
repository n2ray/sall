package io.github.n2ray.sill;

public final class PhoneStreamLayoutTest {
    public static void main(String[] args) {
        assert PhoneStreamLayout.pictureHeight(400, 850, 200, 120) == 250;
        assert PhoneStreamLayout.pictureHeight(320, 500, 200, 120) == 180;
        assert PhoneStreamLayout.pictureHeight(320, 200, 200, 120) == 0;
        assert PhoneStreamLayout.pictureHeight(0, 600, 200, 120) == 0;
        System.out.println("Phone stream layout checks passed");
    }
}
