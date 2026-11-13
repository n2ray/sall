package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/** Kind 18 authenticates the exact JSON bytes, never a re-encoding of its fields. */
final class SignedMacInfo {
    private static final byte[] P256_SPKI = {
            0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x02, 0x01,
            0x06, 0x08, 0x2a, (byte) 0x86, 0x48, (byte) 0xce, 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00};

    record Update(String macId, String name, double issuedAt, boolean remoteAccess, List<SavedMacs.Endpoint> addresses, List<String> kinds, List<Boolean> implicitPorts, int remotePort) {
        Update(String id, String name, double at, boolean remote, List<SavedMacs.Endpoint> addresses) {
            this(id, name, at, remote, addresses, java.util.Collections.nCopies(addresses.size(), "internet"));
        }
        Update(String id, String name, double at, boolean remote, List<SavedMacs.Endpoint> addresses, List<String> kinds) {
            this(id, name, at, remote, addresses, kinds, java.util.Collections.nCopies(addresses.size(), false), 0);
        }
        Update {
            if (macId == null || name == null || !Double.isFinite(issuedAt) || issuedAt <= 0 || remotePort < 0 || remotePort > 65535)
                throw new IllegalArgumentException("Invalid Mac info");
            addresses = List.copyOf(addresses); kinds = List.copyOf(kinds); implicitPorts = List.copyOf(implicitPorts);
            if (kinds.size() != addresses.size() || implicitPorts.size() != addresses.size() || kinds.stream().anyMatch(k -> !java.util.Set.of("vpn", "lan", "internet").contains(k)))
                throw new IllegalArgumentException("Invalid address kind");
            for (int i = 0; i < addresses.size(); i++)
                if (implicitPorts.get(i) && (remotePort == 0 || addresses.get(i).port() != remotePort))
                    throw new IllegalArgumentException("Invalid implicit port");
        }
    }

    static byte[] verify(String info, String key, String signature, byte[] sessionPin) throws java.security.GeneralSecurityException {
        if (sessionPin == null || sessionPin.length != 32 || info == null || info.length() > 65536
                || key == null || key.length() > 88 || signature == null || signature.length() > 100)
            throw new java.security.SignatureException("Invalid Mac info envelope");
        byte[] bytes = Base64.getDecoder().decode(info), point = Base64.getDecoder().decode(key);
        byte[] der = Base64.getDecoder().decode(signature);
        if (bytes.length == 0 || point.length != 65 || point[0] != 4 || der.length < 8 || der.length > 72)
            throw new java.security.SignatureException("Invalid Mac info encoding");
        byte[] spki = ByteBuffer.allocate(P256_SPKI.length + point.length).put(P256_SPKI).put(point).array();
        if (!MessageDigest.isEqual(sessionPin, MessageDigest.getInstance("SHA-256").digest(spki)))
            throw new java.security.SignatureException("Mac info signer differs from session");
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki)));
        verifier.update(bytes);
        if (!verifier.verify(der)) throw new java.security.SignatureException("Invalid Mac info signature");
        return bytes;
    }

    static SavedMacs.Endpoint address(String host, int port) {
        try {
            if (host == null || host.contains("%")) return null;
            PairLink.Address parsed = PairLink.address(host.contains(":") ? "[" + host + "]" : host);
            if (parsed.port() != 0) return null;
            String normalized = parsed.host().toLowerCase(java.util.Locale.ROOT);
            if (normalized.equals("localhost") || normalized.equals("localhost.")) return null;
            if (normalized.contains(":") || normalized.matches("[0-9.]+")) {
                java.net.InetAddress ip = java.net.InetAddress.getByName(normalized);
                if (ip.isLoopbackAddress() || ip.isLinkLocalAddress() || ip.isAnyLocalAddress() || ip.isMulticastAddress()) return null;
                normalized = ip.getHostAddress();
            }
            return new SavedMacs.Endpoint(normalized, port);
        } catch (Exception ignored) { return null; }
    }

    static boolean containsAddress(List<SavedMacs.Endpoint> addresses, List<Boolean> implicitPorts,
                                   SavedMacs.Endpoint address, boolean implicitPort) {
        for (int i = 0; i < addresses.size(); i++)
            if (addresses.get(i).equals(address) && implicitPorts.get(i) == implicitPort) return true;
        return false;
    }
}
