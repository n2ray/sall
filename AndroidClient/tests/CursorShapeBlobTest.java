package io.github.n2ray.sall;

import java.nio.ByteBuffer;

public final class CursorShapeBlobTest {
    public static void main(String[] args) {
        byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        ByteBuffer b = ByteBuffer.allocate(40);
        b.putFloat(3).putFloat(4).putFloat(16).putFloat(24).put(png).putInt(13).putInt(0x49484452).putInt(32).putInt(48);
        byte[] data = b.array();
        var result = CursorShapeBlob.parse(data);
        assert result != null && result.hotX() == 3 && result.hotY() == 4 && result.width() == 16 && result.height() == 24;
        b.putFloat(8, Float.NaN); assert CursorShapeBlob.parse(data) == null;
        b.putFloat(8, 16); b.putInt(32, 1025); assert CursorShapeBlob.parse(data) == null;
        b.putInt(32, 32); data[16] = 0; assert CursorShapeBlob.parse(data) == null;
        assert CursorShapeBlob.parse(new byte[39]) == null;
        assert CursorShapeBlob.parse(new byte[1024 * 1024 + 1]) == null;
        System.out.println("Cursor shape checks passed");
    }
}
