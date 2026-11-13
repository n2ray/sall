package io.github.n2ray.sall;
import java.io.IOException;
import java.util.Arrays;
public class HevcTest {
    public static void main(String[] args) throws Exception {
        var p = Hevc.parameters(new byte[] {4, 2, 0, 2, 0x40, 1, 0, 1, 0x42});
        assert p.lengthSize() == 4;
        assert Arrays.equals(p.codecConfig(), new byte[] {0,0,0,1,0x40,1,0,0,0,1,0x42});
        byte[] csd = java.util.Base64.getDecoder().decode("AAAAAUABDAH//wFgAAADALAAAAMAAAMAPBcCQAAAAAFCAQEBYAAAAwCwAAADAAADADygCAgJFiBe5FkUv/Ln8T+sBagQEBAEAAAAAUQBwHLwUyQ=");
        assert Hevc.size(csd).equals(new Hevc.Size(256, 144)) : Hevc.size(csd);
        assert Hevc.size(sps(4112, 2658)).equals(new Hevc.Size(4112, 2658));
        assert Hevc.size(sps(2056, 1329)).equals(new Hevc.Size(2056, 1329));
        try { Hevc.size(Arrays.copyOf(csd, 35)); throw new AssertionError("accepted truncated SPS"); }
        catch (IOException expected) { }
        assert Arrays.equals(Hevc.frame(new byte[] {0,0,0,2,0x26,1,0,0,0,1,2},4), new byte[] {0,0,0,1,0x26,1,0,0,0,1,2});
        try { Hevc.frame(new byte[] {0,0,0,5,1}, 4); throw new AssertionError("accepted truncated NAL"); }
        catch (IOException expected) { }
        try { Hevc.parameters(new byte[] {4,1,0,2,1}); throw new AssertionError("accepted truncated parameters"); }
        catch (IOException expected) { }
        System.out.println("HEVC framing checks passed");
    }
    private static byte[] sps(int width, int height) {
        StringBuilder bits = new StringBuilder("00000001"); // VPS id, no sublayers, temporal nesting
        bits.append("1".repeat(96)); // profile_tier_level
        for (int value : new int[]{0, 1, width, height}) {
            String binary = Integer.toBinaryString(value + 1);
            bits.append("0".repeat(binary.length() - 1)).append(binary);
        }
        while (bits.length() % 8 != 0) bits.append('1');
        byte[] result = new byte[6 + bits.length() / 8];
        result[3] = 1; result[4] = 0x42; result[5] = 1;
        for (int i = 0; i < bits.length(); i += 8) result[6 + i / 8] = (byte) Integer.parseInt(bits.substring(i, i + 8), 2);
        return result;
    }
}
