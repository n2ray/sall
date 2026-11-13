package io.github.n2ray.sill;

import java.util.Arrays;

/** Strictly unwraps an MPEG-4 ES_Descriptor's nested DecoderSpecificInfo. */
final class EsdsDecoderSpecificInfo {
    private record Descriptor(int tag, int start, int end) { }

    static byte[] extract(byte[] esds) {
        if (esds == null || esds.length < 5 || esds.length > 4096) throw new IllegalArgumentException("Invalid ESDS length");
        Descriptor es = descriptor(esds, 0, esds.length);
        if (es.tag != 3 || es.end != esds.length || es.end - es.start < 3) throw new IllegalArgumentException("Invalid ES descriptor");
        int flags = esds[es.start + 2] & 255, offset = es.start + 3;
        if ((flags & 0x80) != 0) offset = advance(offset, 2, es.end);
        if ((flags & 0x40) != 0) {
            int lenAt = offset;
            offset = advance(offset, 1, es.end);
            offset = advance(offset, esds[lenAt] & 255, es.end);
        }
        if ((flags & 0x20) != 0) offset = advance(offset, 2, es.end);

        byte[] result = null;
        boolean foundConfig = false;
        while (offset < es.end) {
            Descriptor child = descriptor(esds, offset, es.end);
            if (child.tag == 4) {
                if (foundConfig || child.end - child.start < 13) throw new IllegalArgumentException("Invalid decoder config");
                foundConfig = true;
                if ((esds[child.start] & 255) != 0x40 || ((esds[child.start + 1] & 255) >> 2) != 5)
                    throw new IllegalArgumentException("Not MPEG-4 audio");
                int inner = child.start + 13;
                while (inner < child.end) {
                    Descriptor part = descriptor(esds, inner, child.end);
                    if (part.tag == 5) {
                        int length = part.end - part.start;
                        if (result != null || length < 2 || length > 64) throw new IllegalArgumentException("Invalid decoder-specific info");
                        result = Arrays.copyOfRange(esds, part.start, part.end);
                    }
                    inner = part.end;
                }
            }
            offset = child.end;
        }
        if (result == null) throw new IllegalArgumentException("Missing decoder-specific info");
        return result;
    }

    private static int advance(int offset, int count, int end) {
        if (count < 0 || offset > end - count) throw new IllegalArgumentException("Truncated descriptor");
        return offset + count;
    }

    private static Descriptor descriptor(byte[] bytes, int offset, int limit) {
        if (offset >= limit) throw new IllegalArgumentException("Missing descriptor");
        int tag = bytes[offset++] & 255, length = 0;
        boolean complete = false;
        for (int i = 0; i < 4; i++) {
            if (offset >= limit) throw new IllegalArgumentException("Truncated descriptor length");
            int octet = bytes[offset++] & 255;
            length = (length << 7) | (octet & 0x7f);
            if ((octet & 0x80) == 0) { complete = true; break; }
        }
        if (!complete || length > limit - offset) throw new IllegalArgumentException("Invalid descriptor length");
        return new Descriptor(tag, offset, offset + length);
    }
}
