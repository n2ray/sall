package io.github.n2ray.sall;

import java.net.InetAddress;

public final class ViewportFpsTest {
    private static InetAddress ip(int... bytes) throws Exception {
        byte[] value = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) value[i] = (byte) bytes[i];
        return InetAddress.getByAddress(value);
    }
    public static void main(String[] args) throws Exception {
        assert ViewportFps.wanted(120, false, false) == 120;
        assert ViewportFps.wanted(120, true, false) == 60;
        assert ViewportFps.wanted(120, false, true) == 60;
        assert ViewportFps.wanted(50, true, true) == 50;
        assert ViewportFps.wanted(0, false, false) == 30;
        assert !ViewportFps.away(ip(127, 0, 0, 1), true);
        assert !ViewportFps.away(ip(192, 168, 1, 2), false);
        assert !ViewportFps.away(ip(172, 31, 1, 2), false);
        assert ViewportFps.away(ip(172, 32, 1, 2), false);
        assert ViewportFps.away(ip(100, 73, 1, 2), false);
        assert ViewportFps.away(ip(192, 168, 1, 2), true);
        assert ViewportFps.away(null, false);
        assert !ViewportFps.away(ip(0xfd, 0x22, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1), false);
        assert ViewportFps.away(ip(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1), false);
        System.out.println("Viewport frame rate checks passed");
    }
}
