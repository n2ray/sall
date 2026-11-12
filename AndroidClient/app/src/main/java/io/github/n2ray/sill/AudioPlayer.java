package io.github.n2ray.sill;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded AAC-ELD playback; unsupported or malformed streams do not affect video. */
final class AudioPlayer implements AutoCloseable {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.DiscardOldestPolicy());
    private MediaCodec codec;
    private AudioTrack track;
    private int epoch = -1, frameCount, primeFrames, formatPriming, pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private long nextSequence = -1;
    private boolean needsReset;
    private final java.util.function.Consumer<String> onError;

    AudioPlayer(java.util.function.Consumer<String> onError) { this.onError = onError; }

    static boolean supported() {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                try {
                    for (MediaCodecInfo.CodecProfileLevel profile : info.getCapabilitiesForType(type).profileLevels)
                        if (profile.profile == MediaCodecInfo.CodecProfileLevel.AACObjectELD) return true;
                } catch (RuntimeException ignored) { }
            }
        }
        return false;
    }
    void receive(byte[] data) {
        worker.execute(() -> {
            try {
                if (data.length == 0) return;
                if (data[0] == 1) configure(data);
                else if (data[0] == 2) play(AudioPackets.parse(data));
            } catch (Exception e) { android.util.Log.w("SillAndroid", "Audio stopped", e); release(); onError.accept("声音解码失败，画面仍可使用"); }
        });
    }
    private void configure(byte[] data) throws Exception {
        if (data.length > 4096) throw new IllegalArgumentException("Audio format too large");
        JSONObject f = new JSONObject(new String(data, 1, data.length - 1, StandardCharsets.UTF_8));
        if (!"aac-eld".equals(f.optString("codec")) || f.optInt("sampleRate") != 48000 || f.optInt("channels") != 2)
            throw new IllegalArgumentException("Unsupported audio format");
        int frames = f.optInt("framesPerPacket"); int priming = f.optInt("primingFrames");
        int nextEpoch = f.optInt("epoch", -1);
        if (frames < 1 || frames > 8192 || priming < 0 || priming > 8192 || nextEpoch < 0 || nextEpoch > 65535)
            throw new IllegalArgumentException("Invalid audio format");
        byte[] cookie = Base64.decode(f.optString("cookie", ""), Base64.DEFAULT);
        if (cookie.length == 0 || cookie.length > 1024) throw new IllegalArgumentException("Invalid audio configuration");
        release(); epoch = nextEpoch; frameCount = frames; formatPriming = priming; primeFrames = priming; nextSequence = -1; needsReset = false;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(cookie));
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(format, null, null, 0); codec.start();
    }
    private void play(AudioPackets.Batch batch) throws Exception {
        if (codec == null || batch.epoch() != epoch) return;
        if (batch.segmentStart() || needsReset || (nextSequence >= 0 && batch.sequence() != nextSequence)) {
            codec.flush(); if (track != null) { track.pause(); track.flush(); track.play(); }
            primeFrames = batch.segmentStart() ? formatPriming : frameCount;
            needsReset = false;
        }
        for (byte[] packet : batch.packets()) {
            int in = codec.dequeueInputBuffer(0);
            if (in < 0) { needsReset = true; return; }
            ByteBuffer target = codec.getInputBuffer(in); target.clear();
            if (packet.length > target.remaining()) throw new IllegalArgumentException("Audio packet too large");
            target.put(packet); codec.queueInputBuffer(in, 0, packet.length, 0, 0);
            drain();
        }
        nextSequence = (batch.sequence() + batch.packets().size()) & 0xffffffffL;
    }
    private void drain() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (codec != null) {
            int out = codec.dequeueOutputBuffer(info, 0);
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f = codec.getOutputFormat();
                pcmEncoding = f.containsKey(MediaFormat.KEY_PCM_ENCODING) ? f.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT && pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT) throw new IllegalStateException("Unsupported PCM format");
                if (track != null) { track.release(); track = null; }
                int min = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, pcmEncoding);
                if (min <= 0) throw new IllegalStateException("Audio output unavailable");
                track = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(pcmEncoding).build())
                        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min, 48000 * 2 * (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2) / 10)).build();
                track.play();
            } else if (out >= 0) {
                ByteBuffer pcm = codec.getOutputBuffer(out);
                if (pcm != null && track != null && info.size > 0) {
                    int bytesPerFrame = 2 * (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
                    int skip = Math.min(info.size, primeFrames * bytesPerFrame); primeFrames -= skip / bytesPerFrame;
                    pcm.position(info.offset + skip); pcm.limit(info.offset + info.size);
                    track.write(pcm, pcm.remaining(), AudioTrack.WRITE_NON_BLOCKING);
                }
                codec.releaseOutputBuffer(out, false);
            } else break;
        }
    }
    private void release() {
        epoch = -1; nextSequence = -1;
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); codec = null; }
        if (track != null) { try { track.stop(); } catch (Exception ignored) { } track.release(); track = null; }
    }
    @Override public void close() { worker.getQueue().clear(); worker.execute(this::release); worker.shutdown(); }
}
