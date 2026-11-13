package io.github.n2ray.sall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Convert VideoToolbox's length-prefixed NAL units to the Annex B form used by MediaCodec. */
final class Hevc {
    private static final byte[] START = {0, 0, 0, 1};
    record Parameters(int lengthSize, byte[] codecConfig) { }

    static Parameters parameters(byte[] payload) throws IOException {
        if (payload.length < 2) throw new IOException("Truncated HEVC parameters");
        int lengthSize = payload[0] & 255, count = payload[1] & 255, offset = 2;
        if (lengthSize < 1 || lengthSize > 4 || count == 0) throw new IOException("Invalid HEVC parameters");
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < count; i++) {
            if (offset + 2 > payload.length) throw new IOException("Truncated parameter length");
            int length = (payload[offset] & 255) << 8 | payload[offset + 1] & 255;
            offset += 2;
            if (length == 0 || length > payload.length - offset) throw new IOException("Truncated parameter");
            out.write(START); out.write(payload, offset, length); offset += length;
        }
        if (offset != payload.length) throw new IOException("Unexpected HEVC parameter data");
        return new Parameters(lengthSize, out.toByteArray());
    }

    static byte[] frame(byte[] payload, int lengthSize) throws IOException {
        if (lengthSize < 1 || lengthSize > 4) throw new IOException("Invalid NAL length size");
        var out = new ByteArrayOutputStream(payload.length);
        int offset = 0;
        while (offset < payload.length) {
            if (lengthSize > payload.length - offset) throw new IOException("Truncated NAL length");
            long length = 0;
            for (int i = 0; i < lengthSize; i++) length = (length << 8) | (payload[offset++] & 255);
            if (length == 0 || length > payload.length - offset) throw new IOException("Truncated NAL unit");
            out.write(START); out.write(payload, offset, (int) length); offset += (int) length;
        }
        if (offset == 0) throw new IOException("Empty frame");
        return out.toByteArray();
    }
}
