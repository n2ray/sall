package io.github.n2ray.sall;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Base64;
import android.view.Surface;
import java.nio.ByteBuffer;

/** Decodes one locally generated blue HEVC Main-profile frame; no user data or network. */
public final class VideoCodecProbe {
    private static final String CSD = "AAAAAUABDAH//wFgAAADALAAAAMAAAMAPBcCQAAAAAFCAQEBYAAAAwCwAAADAAADADygCAgJFiBe5FkUv/Ln8T+sBagQEBAEAAAAAUQBwHLwUyQ=";
    private static final String FRAME = "AAAAASgBrzFLRBzQ6msTf/8+RB/qhD+nQyIEpGAlt3KAkQfmLHOeUp74AjZIrTCSYMADaQGJAB7wBdyAAAADAADYgA==";

    public static void main(String[] args) throws Exception {
        MediaCodec preferred = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        try { System.out.println("Default HEVC decoder: " + preferred.getCanonicalName()
                + "; hardwareAccelerated=" + preferred.getCodecInfo().isHardwareAccelerated()); }
        finally { preferred.release(); }
        String name = null;
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder() || info.isAlias() || !info.isSoftwareOnly()) continue;
            for (String type : info.getSupportedTypes()) if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(type)) {
                if (name == null || info.getName().startsWith("c2.android.")) name = info.getName();
            }
        }
        if (name == null) throw new IllegalStateException("No software HEVC decoder advertised");
        SurfaceTexture texture = new SurfaceTexture(0);
        texture.setDefaultBufferSize(256, 144);
        Surface surface = new Surface(texture);
        MediaCodec decoder = null;
        try {
            decoder = MediaCodec.createByCodecName(name);
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(Base64.decode(CSD, Base64.DEFAULT)));
            decoder.configure(format, surface, null, 0);
            decoder.start();
            byte[] frame = Base64.decode(FRAME, Base64.DEFAULT);
            long until = System.nanoTime() + 8_000_000_000L;
            int in;
            do { in = decoder.dequeueInputBuffer(10_000); } while (in < 0 && System.nanoTime() < until);
            if (in < 0) throw new IllegalStateException("No decoder input buffer");
            ByteBuffer buffer = decoder.getInputBuffer(in);
            if (buffer == null || buffer.remaining() < frame.length) throw new IllegalStateException("Decoder input buffer too small");
            buffer.clear(); buffer.put(frame);
            decoder.queueInputBuffer(in, 0, frame.length, 0, MediaCodec.BUFFER_FLAG_KEY_FRAME);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean decoded = false;
            while (System.nanoTime() < until) {
                int out = decoder.dequeueOutputBuffer(info, 10_000);
                if (out >= 0) { decoder.releaseOutputBuffer(out, false); decoded = true; break; }
            }
            if (!decoded) throw new IllegalStateException("No decoded HEVC output");
            System.out.println("PASS: " + name + " decoded a synthetic HEVC Main frame");
        } finally {
            if (decoder != null) { try { decoder.stop(); } catch (RuntimeException ignored) { } decoder.release(); }
            surface.release(); texture.release();
        }
    }
}
