package io.github.n2ray.sall;

import java.net.InetAddress;

/** Bonjour is a hint; an unpaired session is only offered on a numeric local address. */
final class LocalOpenPolicy {
    static boolean allowed(HomeDiscoveryMetadata.Door door, String host) {
        if (door != HomeDiscoveryMetadata.Door.TLS_OPEN || host == null
                || !host.matches("[0-9a-fA-F:.]+(%[A-Za-z0-9_.-]+)?")) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) return false;
            byte[] bytes = address.getAddress();
            if (bytes.length == 4) return address.isSiteLocalAddress() || address.isLinkLocalAddress();
            if (bytes.length != 16) return false;
            if ((bytes[0] & 255) == 0xfd && (bytes[1] & 255) == 0x7a
                    && (bytes[2] & 255) == 0x11 && (bytes[3] & 255) == 0x5c
                    && (bytes[4] & 255) == 0xa1 && (bytes[5] & 255) == 0xe0) return false;
            return address.isLinkLocalAddress() || (bytes[0] & 0xfe) == 0xfc;
        } catch (Exception ignored) { return false; }
    }
    static byte[] bind(byte[] observed, byte[] known) throws java.io.IOException {
        if (observed == null || observed.length != 32 || known != null
                && (known.length != 32 || !java.security.MessageDigest.isEqual(known, observed)))
            throw new java.io.IOException("Mac 身份已变化，请重新配对。");
        return observed.clone();
    }
    private LocalOpenPolicy() {}
}
