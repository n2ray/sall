package io.github.n2ray.sall;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class HomeDiscoveryMetadataTest {
    private static byte[] b(String s) { return s.getBytes(StandardCharsets.US_ASCII); }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(HomeDiscoveryMetadata.parse(null).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        check(HomeDiscoveryMetadata.parse(Map.of()).door() == HomeDiscoveryMetadata.Door.LEGACY_PLAIN);
        check(HomeDiscoveryMetadata.parse(Map.of("p", b("0"))).door() == HomeDiscoveryMetadata.Door.TLS_OPEN);
        check(HomeDiscoveryMetadata.parse(Map.of("P", b("0"))).door() == HomeDiscoveryMetadata.Door.TLS_OPEN);
        for (byte[] value : new byte[][]{b("1"), b(""), b("00"), b("2"), new byte[]{(byte) 0xff}})
            check(HomeDiscoveryMetadata.parse(Map.of("p", value)).door() == HomeDiscoveryMetadata.Door.TLS_PAIRING_REQUIRED);
        Map<String, byte[]> bare = new HashMap<>(); bare.put("p", null);
        check(HomeDiscoveryMetadata.parse(bare).door() == HomeDiscoveryMetadata.Door.TLS_PAIRING_REQUIRED);
        check(HomeDiscoveryMetadata.parse(Map.of("p", b("0"), "P", b("1"))).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        Map<String, byte[]> malformed = new HashMap<>(); malformed.put(null, b("0"));
        check(HomeDiscoveryMetadata.parse(malformed).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        check(HomeDiscoveryMetadata.parse(Map.of("", b("0"))).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        check(HomeDiscoveryMetadata.parse(Map.of("p=", b("0"))).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        Map<String, byte[]> tooMany = new HashMap<>(); for (int i = 0; i < 65; i++) tooMany.put("key" + i, b("0"));
        check(HomeDiscoveryMetadata.parse(tooMany).door() == HomeDiscoveryMetadata.Door.UNKNOWN);
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 0x11);
        byte[] tag = b("oaKjpKWmL4V47Ctm");
        var first = HomeDiscoveryMetadata.parse(Map.of("p", b("1"), "r", tag));
        var replay = HomeDiscoveryMetadata.parse(Map.of("p", b("0"), "r", tag));
        check(first.hintsAt(key) && replay.hintsAt(key)); // Replayed r never establishes session trust.
        check(first.recognitionHint().equals(HomeDiscoveryMetadata.parse(Map.of("P", b("1"), "R", tag)).recognitionHint()));
        check(!first.hintsAt(new byte[32]));
        check(!HomeDiscoveryMetadata.parse(Map.of("r", b("invalid"))).hasHint());
        check(!HomeDiscoveryMetadata.parse(Map.of("r", tag, "R", tag)).hasHint());
        var plain = HomeDiscoveryMetadata.Door.LEGACY_PLAIN;
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(plain, false, false));
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(plain, false, true));
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(plain, true, true));
        check(HomeDiscoveryMetadata.legacyDebugAllowed(plain, true, false));
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(HomeDiscoveryMetadata.Door.UNKNOWN, true, false));
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(HomeDiscoveryMetadata.Door.TLS_OPEN, true, false));
        check(!HomeDiscoveryMetadata.legacyDebugAllowed(HomeDiscoveryMetadata.Door.TLS_PAIRING_REQUIRED, true, false));
        System.out.println("Home discovery door and untrusted recognition hints passed");
    }
}
