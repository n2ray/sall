package io.github.n2ray.sall;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Sill kind 43 packet batches, parsed before handing them to an audio decoder. */
final class AudioPackets {
    record Batch(int epoch, long sequence, boolean segmentStart, List<byte[]> packets) { }
    static boolean needsFlush(boolean submitted, boolean segmentStart, boolean lost, long expected, long sequence) {
        return submitted && (segmentStart || lost || (expected >= 0 && expected != sequence));
    }
    static int primingFrames(boolean segmentStart, int frameCount, int formatPriming) {
        return segmentStart ? formatPriming : frameCount;
    }
    static Batch parse(byte[] bytes) throws IOException {
        if (bytes.length < 9 || bytes.length > 16384 || bytes[0] != 2) throw new IOException("Invalid audio packet header");
        ByteBuffer b = ByteBuffer.wrap(bytes);
        b.get(); int epoch = Short.toUnsignedInt(b.getShort()); long sequence = Integer.toUnsignedLong(b.getInt());
        int flags = Byte.toUnsignedInt(b.get()), count = Byte.toUnsignedInt(b.get());
        if (count == 0) throw new IOException("Empty audio batch");
        List<byte[]> packets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (b.remaining() < 2) throw new IOException("Truncated audio packet length");
            int length = Short.toUnsignedInt(b.getShort());
            if (length > b.remaining()) throw new IOException("Truncated audio packet");
            byte[] packet = new byte[length]; b.get(packet); packets.add(packet);
        }
        if (b.hasRemaining()) throw new IOException("Trailing audio data");
        return new Batch(epoch, sequence, (flags & 1) != 0, packets);
    }
}
