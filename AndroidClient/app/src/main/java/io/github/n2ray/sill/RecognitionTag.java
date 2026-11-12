package io.github.n2ray.sill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A replayable discovery hint; TLS certificate pinning remains mandatory. */
final class RecognitionTag {
    static boolean matches(String tag, byte[] key) {
        if (tag == null || tag.length() != 16 || key == null || key.length != 32) return false;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(tag);
            if (decoded.length != 12 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(tag)) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update("sill-tag-v1".getBytes(StandardCharsets.UTF_8));
            mac.update(decoded, 0, 6);
            byte[] expected = java.util.Arrays.copyOf(mac.doFinal(), 6);
            return MessageDigest.isEqual(expected, java.util.Arrays.copyOfRange(decoded, 6, 12));
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    static String fromTxt(byte[] bytes) {
        if (bytes == null || bytes.length != 16) return null;
        for (byte b : bytes) {
            int c = b & 255;
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-' && c != '_') return null;
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }
}
