package io.github.n2ray.sill;

import java.util.Arrays;

public final class EsdsDecoderSpecificInfoTest {
    private static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    private static void reject(byte[] value) {
        try { EsdsDecoderSpecificInfo.extract(value); throw new AssertionError("accepted malformed ESDS"); }
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
        require(Arrays.equals(EsdsDecoderSpecificInfo.extract(a480), hex("f8e65000")));
        require(Arrays.equals(EsdsDecoderSpecificInfo.extract(a512), hex("f8e64000")));
        require(Arrays.equals(EsdsDecoderSpecificInfo.extract(esWith(descriptor(5, hex("f8e65000")))), hex("f8e65000")));
        reject(Arrays.copyOf(a480, a480.length - 1));
        byte[] badTag = a480.clone(); badTag[0] = 4; reject(badTag);
        byte[] wrongType = a480.clone(); wrongType[13] = 0x20; reject(wrongType);
        reject(esWith(descriptor(5, hex("f8e65000")), descriptor(5, hex("f8e64000"))));
        reject(esWith(descriptor(5, new byte[]{1})));
        reject(esWith(descriptor(6, hex("05f8e65000"))));
        reject(hex("038080808000"));
        reject(join(a480, new byte[]{0}));
        System.out.println("ESDS descriptor validation and AAC decoder-specific extraction passed");
    }
}
