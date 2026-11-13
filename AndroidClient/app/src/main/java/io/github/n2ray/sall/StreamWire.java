package io.github.n2ray.sall;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Sill's 14-byte big-endian message header; payload limits match StreamMessage.swift. */
final class StreamWire {
    static final int FRAME = 1;
    static final int MAX_FRAME = 32 << 20;
    static final int MAX_OTHER = 4 << 20;
    static final int MAX_OUTGOING = 1 << 20;

    record Message(int kind, double timestamp, boolean keyframe, byte[] payload) { }

    static Message read(InputStream input) throws IOException {
        return read(input, MAX_FRAME);
    }

    static Message read(InputStream input, int ceiling) throws IOException {
        DataInputStream in = new DataInputStream(input);
        int kind = in.readUnsignedByte();
        double timestamp = in.readDouble();
        boolean keyframe = in.readUnsignedByte() == 1;
        long length = Integer.toUnsignedLong(in.readInt());
        if (length > Math.min(ceiling, kind == FRAME ? MAX_FRAME : MAX_OTHER)) throw new IOException("Sill payload too large");
        byte[] payload = new byte[(int) length];
        in.readFully(payload);
        return new Message(kind, timestamp, keyframe, payload);
    }

    static void write(OutputStream output, int kind, byte[] payload) throws IOException {
        if (kind < 0 || kind > 255 || payload.length > MAX_OUTGOING) throw new IOException("Invalid Sill message");
        DataOutputStream out = new DataOutputStream(output);
        out.writeByte(kind);
        out.writeDouble(System.currentTimeMillis() / 1000.0);
        out.writeByte(0);
        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }
}
