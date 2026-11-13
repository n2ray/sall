package io.github.n2ray.sall;
import java.io.IOException;
import java.util.Arrays;
public class AudioPacketsTest {
    public static void main(String[] args) throws Exception {
        byte[] wire = {2, 0x12, 0x34, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xfe, 1, 2, 0, 2, 10, 11, 0, 1, 12};
        var batch = AudioPackets.parse(wire);
        assert batch.epoch() == 0x1234 && batch.sequence() == 4294967294L && batch.segmentStart();
        assert batch.packets().size() == 2 && Arrays.equals(batch.packets().get(0), new byte[] {10, 11});
        assert !AudioPackets.needsFlush(false, true, false, -1, 0);
        assert !AudioPackets.needsFlush(true, false, false, 3, 3);
        assert AudioPackets.needsFlush(true, true, false, 3, 3);
        assert AudioPackets.needsFlush(true, false, true, 3, 3);
        assert AudioPackets.needsFlush(true, false, false, 3, 4);
        assert AudioPackets.primingFrames(true, 480, 240) == 240;
        assert AudioPackets.primingFrames(false, 480, 240) == 480;
        for (byte[] invalid : new byte[][] {Arrays.copyOf(wire, 8), Arrays.copyOf(wire, wire.length - 1), Arrays.copyOf(wire, wire.length + 1), new byte[] {2,0,0,0,0,0,0,0,0}}) {
            try { AudioPackets.parse(invalid); throw new AssertionError("accepted malformed audio"); }
            catch (IOException expected) { }
        }
        System.out.println("Audio packet checks passed");
    }
}
