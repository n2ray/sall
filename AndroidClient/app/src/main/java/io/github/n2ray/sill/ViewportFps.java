package io.github.n2ray.sill;

import java.net.InetAddress;

/** Match iOS: the screen's maximum, capped to 60 for power saving or an away route. */
final class ViewportFps {
    static int wanted(int screenMaximum, boolean powerSave, boolean away) {
        int rate = Math.max(30, screenMaximum);
        return powerSave || away ? Math.min(rate, 60) : rate;
    }

    /** A route we cannot establish to be local is conservatively treated as away. */
    static boolean away(InetAddress address, boolean tunnelOrMobile) {
        if (address == null) return true;
        if (address.isLoopbackAddress()) return false;
        if (tunnelOrMobile) return true;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255;
            return !(a == 10 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168
                    || a == 169 && b == 254);
        }
        if (bytes.length != 16) return true;
        // Tailscale's IPv6 block is a VPN even if its network is not the device default.
        if ((bytes[0] & 255) == 0xfd && (bytes[1] & 255) == 0x7a && (bytes[2] & 255) == 0x11
                && (bytes[3] & 255) == 0x5c && (bytes[4] & 255) == 0xa1 && (bytes[5] & 255) == 0xe0) return true;
        return !address.isLinkLocalAddress() && (bytes[0] & 0xfe) != 0xfc;
    }
}
