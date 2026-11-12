package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AudioPcmWriterTest {
    public static void main(String[] args) throws Exception {
        ByteBuffer pcm = ByteBuffer.wrap(new byte[8]);
        AtomicInteger calls = new AtomicInteger();
        assert AudioPcmWriter.writeFully(pcm, b -> {
            int call = calls.getAndIncrement();
            if (call == 1) return 0;
            int n = Math.min(3, b.remaining());
            b.position(b.position() + n);
            return n;
        }, () -> false);
        assert pcm.remaining() == 0 && calls.get() == 4;

        AtomicBoolean cancelled = new AtomicBoolean();
        pcm = ByteBuffer.wrap(new byte[8]);
        assert !AudioPcmWriter.writeFully(pcm, b -> {
            b.position(b.position() + 2); cancelled.set(true); return 2;
        }, cancelled::get);
        assert pcm.remaining() == 6;

        boolean failed = false;
        try { AudioPcmWriter.writeFully(ByteBuffer.wrap(new byte[2]), b -> -6, () -> false); }
        catch (IllegalStateException expected) { failed = true; }
        assert failed;
        System.out.println("Audio partial write checks passed");
    }
}
