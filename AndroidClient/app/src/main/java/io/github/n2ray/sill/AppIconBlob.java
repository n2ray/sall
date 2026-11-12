package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Sill kind 4: two-byte big-endian bundle ID length, UTF-8 ID, then PNG. */
final class AppIconBlob {
    record Icon(String bundleId, int imageOffset) { }
    static Icon parse(byte[] data) {
        if (data.length < 4 || data.length > 1024 * 1024) return null;
        int size = (data[0] & 255) << 8 | (data[1] & 255);
        if (size == 0 || size > 512 || 2 + size >= data.length) return null;
        try {
            String id = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, 2, size)).toString();
            return new Icon(id, size + 2);
        } catch (java.nio.charset.CharacterCodingException e) { return null; }
    }
}
