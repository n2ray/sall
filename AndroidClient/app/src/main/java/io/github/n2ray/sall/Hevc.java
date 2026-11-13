package io.github.n2ray.sall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Convert VideoToolbox's length-prefixed NAL units to the Annex B form used by MediaCodec. */
final class Hevc {
    private static final byte[] START = {0, 0, 0, 1};
    record Parameters(int lengthSize, byte[] codecConfig) { }
    record Size(int width, int height) { }

    static Size size(byte[] config) throws IOException {
        for (int i = 0; i + 6 < config.length; i++) {
            if (config[i] != 0 || config[i + 1] != 0 || config[i + 2] != 0 || config[i + 3] != 1
                    || ((config[i + 4] & 0x7e) >> 1) != 33) continue;
            int end = i + 6;
            while (end + 3 < config.length && !(config[end] == 0 && config[end + 1] == 0
                    && config[end + 2] == 0 && config[end + 3] == 1)) end++;
            if (end + 3 >= config.length) end = config.length;
            var rbsp = new ByteArrayOutputStream();
            int zeros = 0;
            for (int j = i + 6; j < end; j++) {
                int b = config[j] & 255;
                if (zeros >= 2 && b == 3) { zeros = 0; continue; }
                rbsp.write(b); zeros = b == 0 ? zeros + 1 : 0;
            }
            Bits bits = new Bits(rbsp.toByteArray());
            bits.read(4); int layers = bits.read(3); bits.read(1);
            bits.skip(96);
            boolean[] profile = new boolean[layers], level = new boolean[layers];
            for (int j = 0; j < layers; j++) { profile[j] = bits.read(1) != 0; level[j] = bits.read(1) != 0; }
            if (layers > 0) bits.skip((8 - layers) * 2);
            for (int j = 0; j < layers; j++) { if (profile[j]) bits.skip(88); if (level[j]) bits.skip(8); }
            bits.ue(); int chroma = bits.ue();
            if (chroma > 3) throw new IOException("Invalid HEVC chroma format");
            if (chroma == 3) bits.read(1);
            int width = bits.ue(), height = bits.ue();
            if (width < 1 || height < 1 || width > 65536 || height > 65536) throw new IOException("Invalid HEVC size");
            return new Size(width, height);
        }
        throw new IOException("Missing HEVC sequence parameters");
    }

    private static final class Bits {
        final byte[] data; int position;
        Bits(byte[] data) { this.data = data; }
        void skip(int count) throws IOException { if (count > data.length * 8 - position) throw new IOException("Truncated HEVC SPS"); position += count; }
        int read(int count) throws IOException {
            if (count > 31 || count > data.length * 8 - position) throw new IOException("Truncated HEVC SPS");
            int value = 0;
            for (int i = 0; i < count; i++, position++) value = (value << 1) | ((data[position / 8] >> (7 - position % 8)) & 1);
            return value;
        }
        int ue() throws IOException {
            int zeros = 0;
            while (read(1) == 0) if (++zeros > 30) throw new IOException("Invalid HEVC SPS number");
            return ((1 << zeros) - 1) + read(zeros);
        }
    }

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
