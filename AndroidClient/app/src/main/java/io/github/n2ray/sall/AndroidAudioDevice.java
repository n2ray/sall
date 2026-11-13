package io.github.n2ray.sall;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.nio.ByteBuffer;

/** Android-only hardware boundary; every call is made by AudioPlayer's serial owner. */
final class AndroidAudioDevice implements AudioRuntime.Device {
    private MediaCodec codec;
    private AudioTrack track;
    private MediaFormat format;
    private AudioDecodeMapping.Encoding encoding = AudioDecodeMapping.Encoding.S16_LE;
    private Integer route;
    private final AudioTimestamp timestamp = new AudioTimestamp();

    @Override public void open(byte[] cookie) throws Exception {
        close();
        format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(AacEldConfig.parse(cookie).asc()));
        startDecoder();
        int minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (minimum <= 0) throw new IllegalStateException("Float stereo output unavailable");
        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum, 960 * 8)).build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack unavailable");
        track.play(); route = null;
    }
    private void startDecoder() throws Exception {
        String name = AudioPlayer.decoderName();
        if (name == null) throw new IllegalStateException("AAC-ELD decoder unavailable");
        codec = MediaCodec.createByCodecName(name);
        codec.configure(format, null, null, 0); codec.start();
        encoding = AudioDecodeMapping.Encoding.S16_LE;
    }
    @Override public void resetDecoder() throws Exception {
        // A new codec instance makes the flush generation boundary unambiguous even before first output.
        releaseDecoder(); startDecoder();
    }
    @Override public boolean offer(byte[] packet, long ptsUs) {
        int index = codec.dequeueInputBuffer(0);
        if (index < 0) return false;
        ByteBuffer input = codec.getInputBuffer(index);
        if (input == null || input.capacity() < packet.length) throw new IllegalStateException("Invalid AAC input buffer");
        input.clear(); input.put(packet);
        codec.queueInputBuffer(index, 0, packet.length, ptsUs, 0);
        return true;
    }
    @Override public AudioRuntime.PCM poll() {
        var info = new MediaCodec.BufferInfo();
        for (int attempt = 0; attempt < 4; attempt++) {
            int index = codec.dequeueOutputBuffer(info, 0);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                var f = codec.getOutputFormat();
                if (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) != 48000 || f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2)
                    throw new IllegalStateException("Unsupported decoded PCM layout");
                int value = f.containsKey(MediaFormat.KEY_PCM_ENCODING) ? f.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                if (value == AudioFormat.ENCODING_PCM_FLOAT) encoding = AudioDecodeMapping.Encoding.FLOAT32_LE;
                else if (value == AudioFormat.ENCODING_PCM_16BIT) encoding = AudioDecodeMapping.Encoding.S16_LE;
                else throw new IllegalStateException("Unsupported decoded PCM encoding");
            } else if (index >= 0) {
                try {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) throw new IllegalStateException("Unexpected AAC end of stream");
                    if (info.size == 0 || (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) continue;
                    ByteBuffer source = codec.getOutputBuffer(index);
                    if (source == null || info.offset < 0 || info.size < 0 || info.size > 8192 * 8 * 4
                            || info.offset > source.capacity() - info.size) throw new IllegalStateException("Invalid decoded PCM buffer");
                    source = source.duplicate(); source.clear(); source.position(info.offset); source.limit(info.offset + info.size);
                    ByteBuffer copy = ByteBuffer.allocate(info.size); copy.put(source); copy.flip();
                    return new AudioRuntime.PCM(info.presentationTimeUs, copy, encoding);
                } finally { codec.releaseOutputBuffer(index, false); }
            } else return null;
        }
        return null;
    }
    @Override public AudioRuntime.Stamp timestamp() {
        return track.getTimestamp(timestamp) ? new AudioRuntime.Stamp(timestamp.framePosition, timestamp.nanoTime) : null;
    }
    @Override public int write(float[] pcm, int offset, int count) {
        return track.write(pcm, offset, count, AudioTrack.WRITE_NON_BLOCKING);
    }
    @Override public int bufferFrames() { return track.getBufferSizeInFrames(); }
    @Override public int underruns() { return track.getUnderrunCount(); }
    @Override public boolean routeChanged() {
        AudioDeviceInfo device = track.getRoutedDevice();
        if (device == null) return route != null;
        int id = device.getId();
        if (route == null) { route = id; return false; }
        return route != id;
    }
    private void releaseDecoder() {
        if (codec == null) return;
        try { codec.stop(); } catch (RuntimeException ignored) { }
        try { codec.release(); } finally { codec = null; }
    }
    @Override public void close() {
        try { releaseDecoder(); } finally {
            if (track != null) {
                try { track.pause(); track.flush(); } catch (RuntimeException ignored) { }
                try { track.release(); } finally { track = null; }
            }
            route = null;
        }
    }
}
