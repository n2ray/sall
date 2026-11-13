package io.github.n2ray.sall;

public final class AppIconBlobTest {
    public static void main(String[] args) {
        var icon = AppIconBlob.parse(new byte[]{0, 3, 'a', '.', 'b', 1});
        assert icon != null && icon.bundleId().equals("a.b") && icon.imageOffset() == 5;
        assert AppIconBlob.parse(new byte[]{0, 3, 'a', '.', 'b'}) == null;
        assert AppIconBlob.parse(new byte[]{0, 1, (byte) 0xff, 1}) == null;
        assert AppIconBlob.parse(new byte[]{0, 0, 1, 1}) == null;
        System.out.println("App icon framing checks passed");
    }
}
