package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Sill kind 14: four big-endian floats followed by a bounded PNG. */
final class CursorShapeBlob {
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    record Shape(float hotX, float hotY, float width, float height) { }

    static Shape parse(byte[] data) {
        if (data.length < 40 || data.length > 1024 * 1024) return null;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        float x = b.getFloat(), y = b.getFloat(), width = b.getFloat(), height = b.getFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0 || height <= 0 || width > 256 || height > 256
                || x < 0 || y < 0 || x > width || y > height) return null;
        for (byte expected : PNG) if (b.get() != expected) return null;
        if (b.getInt() != 13 || b.getInt() != 0x49484452) return null;
        int pixelsWide = b.getInt(), pixelsHigh = b.getInt();
        if (pixelsWide <= 0 || pixelsHigh <= 0 || pixelsWide > 1024 || pixelsHigh > 1024) return null;
        return new Shape(x, y, width, height);
    }
}
