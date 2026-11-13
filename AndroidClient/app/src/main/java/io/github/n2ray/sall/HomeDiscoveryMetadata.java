package io.github.n2ray.sall;

import java.util.Map;

/** Unauthenticated Bonjour metadata; neither a hint match nor a door advert verifies a Mac. */
final class HomeDiscoveryMetadata {
    enum Door { UNKNOWN, LEGACY_PLAIN, TLS_OPEN, TLS_PAIRING_REQUIRED }
    private final Door door;
    private final String recognitionHint;
    private HomeDiscoveryMetadata(Door door, String recognitionHint) {
        this.door = door; this.recognitionHint = recognitionHint;
    }
    Door door() { return door; }
    /** A replayable candidate hint only. A pinned TLS handshake is still required. */
    boolean hintsAt(byte[] recognitionKey) { return RecognitionTag.matches(recognitionHint, recognitionKey); }
    boolean hasHint() { return recognitionHint != null; }
    String recognitionHint() { return recognitionHint; }

    /** Null metadata means unavailable; an observed empty TXT record means no p key. */
    static HomeDiscoveryMetadata parse(Map<String, byte[]> txt) {
        if (txt == null || txt.size() > 64) return new HomeDiscoveryMetadata(Door.UNKNOWN, null);
        boolean hasDoor = false, hasHint = false;
        byte[] doorValue = null, hintValue = null;
        for (Map.Entry<String, byte[]> entry : txt.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isEmpty() || key.length() > 255)
                return new HomeDiscoveryMetadata(Door.UNKNOWN, null);
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (c < 0x20 || c > 0x7e || c == '=') return new HomeDiscoveryMetadata(Door.UNKNOWN, null);
            }
            if (key.equals("p") || key.equals("P")) {
                if (hasDoor) return new HomeDiscoveryMetadata(Door.UNKNOWN, null);
                hasDoor = true; doorValue = entry.getValue();
            } else if (key.equals("r") || key.equals("R")) {
                if (hasHint) return new HomeDiscoveryMetadata(Door.UNKNOWN, null);
                hasHint = true; hintValue = entry.getValue();
            }
        }
        Door door = !hasDoor ? Door.LEGACY_PLAIN
                : doorValue != null && doorValue.length == 1 && doorValue[0] == '0'
                    ? Door.TLS_OPEN : Door.TLS_PAIRING_REQUIRED;
        return new HomeDiscoveryMetadata(door, RecognitionTag.fromTxt(hintValue));
    }

    /** Pure compatibility rule only; production call sites do not dial legacy doors. */
    static boolean legacyDebugAllowed(Door door, boolean debugBuild, boolean macPreviouslyUsedTls) {
        return door == Door.LEGACY_PLAIN && debugBuild && !macPreviouslyUsedTls;
    }
    private HomeDiscoveryMetadata() { throw new AssertionError(); }
}
