package io.github.n2ray.sall;

import java.io.*;
import java.util.Arrays;

public class StreamWireTest {
    public static void main(String[] args) throws Exception {
        var out = new ByteArrayOutputStream();
        StreamWire.write(out, 8, new byte[] {1, 2, 3});
        var message = StreamWire.read(new ByteArrayInputStream(out.toByteArray()));
        assert message.kind() == 8 && !message.keyframe() && Arrays.equals(message.payload(), new byte[] {1, 2, 3});
        byte[] oversized = new byte[14]; oversized[0] = 2; oversized[10] = 0x7f;
        try { StreamWire.read(new ByteArrayInputStream(oversized)); throw new AssertionError("accepted oversized payload"); }
        catch (IOException expected) { }
        try { StreamWire.read(new ByteArrayInputStream(new byte[] {1, 2})); throw new AssertionError("accepted truncated header"); }
        catch (EOFException expected) { }
        System.out.println("Sill wire checks passed");
    }
}
