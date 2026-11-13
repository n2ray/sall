package io.github.n2ray.sall;

import java.util.Arrays;

public final class AacEldConfigTest {
    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    private static void reject(byte[] value) {
        try { AacEldConfig.parse(value); throw new AssertionError("accepted malformed ESDS"); }
        catch (IllegalArgumentException expected) { }
    }
    private static byte[] join(byte[]... values) {
        int count = 0; for (byte[] value : values) count += value.length;
        byte[] out = new byte[count]; int at = 0;
        for (byte[] value : values) { System.arraycopy(value, 0, out, at, value.length); at += value.length; }
        return out;
    }
    private static byte[] descriptor(int tag, byte[] payload) {
        if (payload.length > 127) throw new AssertionError();
        return join(new byte[]{(byte) tag, (byte) payload.length}, payload);
    }
    private static byte[] esWith(byte[]... parts) {
        byte[] decoder = descriptor(4, join(hex("40140018000001f4000001f400"), join(parts)));
        return descriptor(3, join(hex("000000"), decoder, descriptor(6, new byte[]{2})));
    }
    public static void main(String[] args) {
        String head = "0380808024000000048080801640140018000001f4000001f4000580808004";
        String tail = "068080800102";
        byte[] a480 = hex(head + "f8e65000" + tail), a512 = hex(head + "f8e64000" + tail);
        require(Arrays.equals(AacEldConfig.parse(a480).asc(), hex("f8e65000")));
        require(Arrays.equals(AacEldConfig.parse(a512).asc(), hex("f8e64000")));
        require(Arrays.equals(AacEldConfig.parse(esWith(descriptor(5, hex("f8e65000")))).asc(), hex("f8e65000")));
        reject(Arrays.copyOf(a480, a480.length - 1));
        byte[] badTag = a480.clone(); badTag[0] = 4; reject(badTag);
        byte[] wrongType = a480.clone(); wrongType[13] = 0x20; reject(wrongType);
        reject(esWith(descriptor(5, hex("f8e65000")), descriptor(5, hex("f8e64000"))));
        reject(esWith(descriptor(5, new byte[]{1})));
        reject(esWith(descriptor(6, hex("05f8e65000"))));
        reject(hex("038080808000"));
        reject(join(a480, new byte[]{0}));
        require(AacEldConfig.parse(hex("f8e65000")).frames() == 480);
        require(AacEldConfig.parse(hex("f8e64000")).frames() == 512);
        require(Arrays.equals(AacEldConfig.parse(hex("f8e65000")).asc(), hex("f8e65000")));
        byte[] bare = hex("f8e65000");
        var isolated = AacEldConfig.parse(bare); bare[0] = 0;
        byte[] returned = isolated.asc(); returned[0] = 0;
        require(isolated.asc()[0] == (byte) 0xf8);
        reject(null); reject(new byte[0]); reject(new byte[1025]);
        reject(hex("1210")); reject(hex("f8e55000")); reject(hex("f8e63000"));
        reject(hex("f8e65800")); reject(hex("f8e65100")); reject(hex("f8e65004"));
        reject(hex("f8e65001")); reject(hex("f8e6500000"));
        for (int i = 0; i < a480.length; i++) reject(Arrays.copyOf(a480, i));
        byte[] sl = a480.clone(); sl[sl.length - 1] = 3; reject(sl);
        byte[] config = descriptor(4, join(hex("40140018000001f4000001f400"), descriptor(5, hex("f8e65000"))));
        byte[] flags = descriptor(3, join(hex("0000e00001036162630002"), config, descriptor(6, new byte[]{2})));
        require(AacEldConfig.parse(flags).frames() == 480);
        reject(descriptor(3, join(hex("000000"), config, config, descriptor(6, new byte[]{2}))));
        reject(descriptor(3, join(hex("000000"), config)));
        String explicit = "11111000111" + "1111" + String.format("%24s", Integer.toBinaryString(48000)).replace(' ', '0')
                + "0010" + "1" + "0000" + "0000" + "00" + "00";
        byte[] explicitASC = new byte[explicit.length() / 8];
        for (int i = 0; i < explicitASC.length; i++) explicitASC[i] = (byte) Integer.parseInt(explicit.substring(i * 8, i * 8 + 8), 2);
        require(AacEldConfig.parse(explicitASC).frames() == 480);
        var random = new java.util.Random(437);
        for (int i = 0; i < 10000; i++) {
            byte[] data = new byte[random.nextInt(1100)]; random.nextBytes(data);
            try { var c = AacEldConfig.parse(data); require(c.frames() == 480 || c.frames() == 512); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("AAC-ELD production ESDS/bare ASC validation passed: Mac 480/512 cookies, explicit rate, optional ES flags, duplicate/truncated/unsupported data, defensive copies, 10000 bounded random inputs");
    }
}
