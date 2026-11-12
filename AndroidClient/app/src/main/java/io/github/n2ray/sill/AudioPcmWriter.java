package io.github.n2ray.sill;

import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;

final class AudioPcmWriter {
    private AudioPcmWriter() { }

    static boolean writeFully(ByteBuffer pcm, ToIntFunction<ByteBuffer> write, BooleanSupplier cancelled)
            throws InterruptedException {
        long lastProgress = System.nanoTime();
        while (pcm.hasRemaining()) {
            if (cancelled.getAsBoolean()) return false;
            int before = pcm.position();
            int written = write.applyAsInt(pcm);
            if (written < 0 || pcm.position() - before != written)
                throw new IllegalStateException("Audio output write failed: " + written);
            if (written > 0) lastProgress = System.nanoTime();
            else {
                // shortcut: a stalled track fails after 500 ms; use playback callbacks when implementing timed audio.
                if (System.nanoTime() - lastProgress >= 500_000_000L)
                    throw new IllegalStateException("Audio output stalled");
                Thread.sleep(2);
            }
        }
        return !cancelled.getAsBoolean();
    }
}
