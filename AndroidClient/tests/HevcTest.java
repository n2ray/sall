package io.github.n2ray.sall;
import java.io.IOException;
import java.util.Arrays;
public class HevcTest {
    public static void main(String[] args) throws Exception {
        var p = Hevc.parameters(new byte[] {4, 2, 0, 2, 0x40, 1, 0, 1, 0x42});
        assert p.lengthSize() == 4;
        assert Arrays.equals(p.codecConfig(), new byte[] {0,0,0,1,0x40,1,0,0,0,1,0x42});
        assert Arrays.equals(Hevc.frame(new byte[] {0,0,0,2,0x26,1,0,0,0,1,2},4), new byte[] {0,0,0,1,0x26,1,0,0,0,1,2});
        try { Hevc.frame(new byte[] {0,0,0,5,1}, 4); throw new AssertionError("accepted truncated NAL"); }
        catch (IOException expected) { }
        try { Hevc.parameters(new byte[] {4,1,0,2,1}); throw new AssertionError("accepted truncated parameters"); }
        catch (IOException expected) { }
        System.out.println("HEVC framing checks passed");
    }
}
