package io.github.n2ray.sill;

import java.nio.charset.StandardCharsets;

/** Decisions for an explicit clipboard send; never stores clipboard contents. */
final class ClipboardPastePolicy {
    static final int MAX_TEXT_BYTES = 256 * 1024;
    static final int MAX_PAYLOAD_BYTES = 1 << 20;
    enum Text { READY, EMPTY, TOO_LONG }

    static Text text(String value, int payloadBytes) {
        if (value == null || value.isEmpty()) return Text.EMPTY;
        if (payloadBytes < 0 || payloadBytes > MAX_PAYLOAD_BYTES || value.length() > MAX_TEXT_BYTES
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) return Text.TOO_LONG;
        return Text.READY;
    }

    // Evaluate at the receipt and again immediately before the delayed key event.
    static boolean paste(boolean rightAway, boolean timely, boolean connected, boolean sharing,
                         boolean hostAccepts, boolean streaming, boolean foreground, boolean inputAllowed) {
        return rightAway && timely && connected && sharing && hostAccepts && streaming && foreground && inputAllowed;
    }
    static boolean sameChipAcknowledgement(long current, long expected) { return expected != 0 && current == expected; }
}
