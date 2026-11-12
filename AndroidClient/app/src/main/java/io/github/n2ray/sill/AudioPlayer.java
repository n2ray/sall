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
            new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.DiscardPolicy());
    private MediaCodec codec;
    private AudioTrack track;
    private int epoch = -1, frameCount, primeFrames, formatPriming, pcmEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private long nextSequence = -1;
    private long presentationUs;
    private boolean needsReset, submitted, hasOutput;
    private MediaFormat decoderFormat;
    private String decoderName;
    private final java.util.function.Consumer<String> onError;
    private volatile long generation;
    private volatile boolean playbackEnabled = true;
    private byte[] latestFormat;
    private boolean closed;

    AudioPlayer(java.util.function.Consumer<String> onError) { this.onError = onError; }

    static boolean supported() {
        return decoderName() != null;
    }
    private static String decoderName() {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                try {
                    for (MediaCodecInfo.CodecProfileLevel profile : info.getCapabilitiesForType(type).profileLevels)
                        if (profile.profile == MediaCodecInfo.CodecProfileLevel.AACObjectELD) return info.getName();
                } catch (RuntimeException ignored) { }
            }
        }
        return null;
    }
    synchronized void receive(byte[] data) {
        if (closed) return;
        // A format supersedes queued audio; keep it ahead of later packets even under backlog.
        if (data.length > 0 && data[0] == 1) {
            generation++; worker.getQueue().clear();
            latestFormat = data.length <= 4096 ? data.clone() : null;
        }
        if (!playbackEnabled) {
            if (data.length > 0 && data[0] == 1) worker.execute(this::release);
            return;
        }
        long expectedGeneration = generation;
        worker.execute(() -> {
            try {
                if (generation != expectedGeneration) return;
                if (data.length == 0) return;
                if (data[0] == 1) configure(data);
                else if (data[0] == 2) play(AudioPackets.parse(data), expectedGeneration);
            } catch (Exception e) { if (generation == expectedGeneration) { android.util.Log.w("SillAndroid", "Audio stopped", e); release(); onError.accept("声音解码失败，画面仍可使用"); } }
        });
    }
    synchronized void setPlaybackEnabled(boolean enabled) {
        if (closed || playbackEnabled == enabled) return;
        playbackEnabled = enabled; generation++; worker.getQueue().clear();
        long expectedGeneration = generation;
        byte[] format = latestFormat;
        worker.execute(() -> {
            if (generation != expectedGeneration) return;
            release();
            if (!enabled || format == null) return;
            try { configure(format); }
            catch (Exception e) {
                if (generation == expectedGeneration) {
                    release(); onError.accept("声音恢复失败，画面仍可使用");
                }
            }
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
        release(); epoch = nextEpoch; frameCount = frames; formatPriming = priming; primeFrames = frames; nextSequence = -1; needsReset = false; presentationUs = 0;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(cookie));
        decoderName = decoderName();
        if (decoderName == null) throw new IllegalStateException("AAC-ELD decoder unavailable");
        decoderFormat = format; startDecoder();
    }
    private void startDecoder() throws Exception {
        codec = MediaCodec.createByCodecName(decoderName);
        codec.configure(decoderFormat, null, null, 0); codec.start();
        submitted = false; hasOutput = false;
    }
    private void play(AudioPackets.Batch batch, long expectedGeneration) throws Exception {
        if (codec == null || batch.epoch() != epoch) return;
        if (batch.segmentStart() || needsReset || (nextSequence >= 0 && batch.sequence() != nextSequence)) {
            if (AudioPackets.needsFlush(submitted, batch.segmentStart(), needsReset, nextSequence, batch.sequence())) {
                if (hasOutput) { codec.flush(); submitted = false; }
                else { codec.stop(); codec.release(); codec = null; startDecoder(); }
            }
            if (track != null) { track.pause(); track.flush(); track.play(); }
            primeFrames = AudioPackets.primingFrames(batch.segmentStart(), frameCount, formatPriming);
            needsReset = false;
        }
        for (byte[] packet : batch.packets()) {
            if (generation != expectedGeneration) return;
            drain(expectedGeneration);
            if (generation != expectedGeneration) return;
            int in = codec.dequeueInputBuffer(10000);
            if (in < 0) { needsReset = true; return; }
            ByteBuffer target = codec.getInputBuffer(in); target.clear();
            if (packet.length > target.remaining()) throw new IllegalArgumentException("Audio packet too large");
            target.put(packet); codec.queueInputBuffer(in, 0, packet.length, presentationUs, 0);
            presentationUs += frameCount * 1000000L / 48000; submitted = true;
            drain(expectedGeneration);
        }
        nextSequence = (batch.sequence() + batch.packets().size()) & 0xffffffffL;
    }
    private void drain(long expectedGeneration) throws InterruptedException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (codec != null) {
            int out = codec.dequeueOutputBuffer(info, 0);
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                hasOutput = true;
                MediaFormat f = codec.getOutputFormat();
                if (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) != 48000 || f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2)
                    throw new IllegalStateException("Unsupported PCM channel layout or rate");
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
                hasOutput = true;
                ByteBuffer pcm = codec.getOutputBuffer(out);
                if (pcm != null && track != null && info.size > 0) {
                    int bytesPerFrame = 2 * (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
                    int skip = Math.min(info.size, primeFrames * bytesPerFrame); primeFrames -= skip / bytesPerFrame;
                    pcm.position(info.offset + skip); pcm.limit(info.offset + info.size);
                    if (!AudioPcmWriter.writeFully(pcm,
                            buffer -> track.write(buffer, buffer.remaining(), AudioTrack.WRITE_NON_BLOCKING),
                            () -> generation != expectedGeneration || !playbackEnabled)) {
                        codec.releaseOutputBuffer(out, false);
                        return;
                    }
                }
                codec.releaseOutputBuffer(out, false);
            } else break;
        }
    }
    private void release() {
        epoch = -1; nextSequence = -1; decoderFormat = null; decoderName = null; submitted = hasOutput = false;
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); codec = null; }
        if (track != null) { try { track.stop(); } catch (Exception ignored) { } track.release(); track = null; }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true; generation++; latestFormat = null;
        worker.getQueue().clear(); worker.execute(this::release); worker.shutdown();
    }
}
