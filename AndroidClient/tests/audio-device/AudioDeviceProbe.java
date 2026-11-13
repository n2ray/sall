package io.github.n2ray.sill;

import android.app.Instrumentation;
import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.util.Properties;

/** Local synthetic-silence probe. It never connects to the host or changes system volume. */
public final class AudioDeviceProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        AudioProbeChecks.Report report = AudioProbeChecks.runWithRuntime(
                AudioDeviceProbe::probeTrack,
                () -> decodeScenario(480, false), () -> decodeScenario(480, true),
                () -> decodeScenario(512, false), () -> decodeScenario(512, true),
                () -> runtimeScenario(480, false), () -> runtimeScenario(480, true),
                () -> runtimeScenario(512, false), () -> runtimeScenario(512, true));
        result.putString("result", (report.passed() ? "PASS" : "FAIL") + ": " + report.detail());
        finish(report.passed() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private AudioProbeChecks.Result decodeScenario(int frames, boolean decoderSpecific) throws Exception {
        String decoder = decoderName();
        if (decoder == null) return new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "no advertised AAC-ELD decoder");
        Properties fixture = new Properties();
        try (var input = getTargetContext().getAssets().open("audio-silence.properties")) { fixture.load(input); }
        return new AudioProbeChecks.Result(AudioProbeChecks.Status.PASS, decode(fixture, decoder, frames, decoderSpecific));
    }

    private AudioProbeChecks.Result runtimeScenario(int frames, boolean decoderSpecific) throws Exception {
        if (decoderName() == null) return new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "no advertised AAC-ELD decoder");
        Properties fixture = new Properties();
        try (var input = getTargetContext().getAssets().open("audio-silence.properties")) { fixture.load(input); }
        return AudioRuntimeProbe.run(fixture, frames, decoderSpecific);
    }

    private record TrackResult(boolean ok, String description) { }

    private static AudioProbeChecks.Result probeTrack() {
        TrackResult legacy = safeTrackScenario("legacy960/15ms", false, false);
        TrackResult production = safeTrackScenario("production240/5ms", true, false);
        TrackResult prefilled = safeTrackScenario("prefill240/5ms", true, true);
        String details = legacy.description + " || " + production.description + " || " + prefilled.description;
        return new AudioProbeChecks.Result(legacy.ok && production.ok && prefilled.ok
                ? AudioProbeChecks.Status.PASS : AudioProbeChecks.Status.FAIL, details);
    }

    private static TrackResult safeTrackScenario(String label, boolean productionCadence, boolean prefill) {
        try { return trackScenario(label, productionCadence, prefill); }
        catch (Throwable e) { return new TrackResult(false, label + ": " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private static TrackResult trackScenario(String label, boolean productionCadence, boolean prefill) throws Exception {
        int min = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        if (min <= 0) throw new IllegalStateException("float stereo AudioTrack unavailable");
        long createdAt = System.nanoTime();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min, 960 * 8)).build();
        try {
            if (track.getState() != AudioTrack.STATE_INITIALIZED || track.setVolume(0f) != AudioTrack.SUCCESS)
                throw new IllegalStateException("cannot guarantee silent output");
            float[] silence = new float[960 * 2];
            int bufferFrames = track.getBufferSizeInFrames(), writes = 0;
            long firstWriteNanos = -1;
            if (prefill) {
                int targetFloats = Math.min(4800, bufferFrames) * 2;
                while (writes < targetFloats) {
                    if (firstWriteNanos < 0) firstWriteNanos = System.nanoTime();
                    int requested = Math.min(silence.length, targetFloats - writes);
                    int n = track.write(silence, 0, requested, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0 || n > requested) throw new IllegalStateException("AudioTrack prefill " + n);
                    if (n == 0) break;
                    writes += n;
                }
            }
            int prefillFrames = writes / 2;
            track.play();
            AudioTimestamp stamp = new AudioTimestamp();
            long startNanos = System.nanoTime();
            AudioPresentationClock productionClock = new AudioPresentationClock(startNanos);
            StringBuilder samples = new StringBuilder();
            long firstFrame = -1, firstNanos = -1, lastFrame = -1, lastNanos = -1;
            long stableFrame = -1, stableNanos = -1, endFrame = -1, endNanos = -1;
            long end = SystemClock.elapsedRealtime() + 1200;
            int timestamps = 0, logged = 0, polls = 0, pendingFloats = 0;
            while (SystemClock.elapsedRealtime() < end) {
                if (!productionCadence) {
                    if (firstWriteNanos < 0) firstWriteNanos = System.nanoTime();
                    int n = track.write(silence, 0, silence.length, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0 || n > silence.length) throw new IllegalStateException("AudioTrack write " + n);
                    writes += n;
                }
                long before = System.nanoTime();
                boolean available = track.getTimestamp(stamp);
                long after = System.nanoTime();
                AudioPresentationClock.State state = AudioProbeClock.sample(productionClock, available,
                        available ? stamp.framePosition : 0, available ? stamp.nanoTime : 0, before, after, writes / 2L);
                if (available && stamp.nanoTime > 0 && stamp.framePosition >= 0) {
                    if (firstFrame < 0) { firstFrame = stamp.framePosition; firstNanos = stamp.nanoTime; timestamps = 1; }
                    else if (timestamps == 1 && stamp.framePosition > firstFrame && stamp.nanoTime > firstNanos) {
                        lastFrame = stamp.framePosition; lastNanos = stamp.nanoTime; timestamps = 2;
                    }
                    if (before - startNanos >= 300_000_000L) {
                        if (stableFrame < 0) { stableFrame = stamp.framePosition; stableNanos = stamp.nanoTime; }
                        else if (stamp.framePosition > stableFrame && stamp.nanoTime > stableNanos) {
                            endFrame = stamp.framePosition; endNanos = stamp.nanoTime;
                        }
                    }
                }
                if (logged < 16 && (polls < 8 || polls % 6 == 0)) {
                    if (samples.length() != 0) samples.append('|');
                    samples.append("ms=").append((before - startNanos) / 1_000_000)
                            .append(",available=").append(available);
                    if (available) samples.append(",frame=").append(stamp.framePosition)
                            .append(",ageBeforeUs=").append((before - stamp.nanoTime) / 1000)
                            .append(",ageAfterUs=").append((after - stamp.nanoTime) / 1000);
                    samples.append(",writtenFrames=").append(writes / 2L)
                            .append(",head=").append(Integer.toUnsignedLong(track.getPlaybackHeadPosition()))
                            .append(",underruns=").append(track.getUnderrunCount())
                            .append(",callUs=").append((after - before) / 1000)
                            .append(",clock=").append(state);
                    logged++;
                }
                if (productionCadence) {
                    int frames = state == AudioPresentationClock.State.STARTING
                            ? productionClock.startupSilence(productionClock.generation(), after)
                            : state == AudioPresentationClock.State.READY ? 240 : 0;
                    pendingFloats += frames * 2;
                }
                if (pendingFloats > 0) {
                    int requested = Math.min(silence.length, pendingFloats);
                    if (firstWriteNanos < 0) firstWriteNanos = System.nanoTime();
                    int n = track.write(silence, 0, requested, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0 || n > requested) throw new IllegalStateException("AudioTrack write " + n);
                    writes += n; pendingFloats -= n;
                }
                polls++;
                Thread.sleep(productionCadence ? 5 : 15);
            }
            long timeDelta = timestamps < 2 ? 0 : lastNanos - firstNanos;
            long frameDelta = timestamps < 2 ? 0 : lastFrame - firstFrame;
            double rate = timeDelta > 0 ? frameDelta * 1_000_000_000.0 / timeDelta : Double.NaN;
            double stableRate = endNanos > stableNanos && stableNanos >= 0
                    ? (endFrame - stableFrame) * 1_000_000_000.0 / (endNanos - stableNanos) : Double.NaN;
            boolean ok = writes > 0 && timestamps >= 2 && (productionCadence
                    ? productionClock.state() == AudioPresentationClock.State.READY
                    : rate >= 40000 && rate <= 56000);
            String details = label + ": scenarioStatus=" + (ok ? "PASS" : "FAIL")
                    + ", firstRate=" + rateText(rate) + ", firstFrameDelta=" + frameDelta
                    + ", firstTimeDeltaNs=" + timeDelta + ", stableRate=" + rateText(stableRate)
                    + ", stableFrameDelta=" + (endFrame - stableFrame) + ", stableTimeDeltaNs=" + (endNanos - stableNanos)
                    + ", bufferFrames=" + bufferFrames + ", prefillFrames=" + prefillFrames
                    + ", finalHead=" + Integer.toUnsignedLong(track.getPlaybackHeadPosition())
                    + ", underruns=" + track.getUnderrunCount() + ", firstWriteAfterPlayUs="
                    + (firstWriteNanos < 0 ? "none" : Long.toString((firstWriteNanos - startNanos) / 1000))
                    + ", playAfterCreateMs=" + ((startNanos - createdAt) / 1_000_000)
                    + ", writesFloat=" + writes + ", pendingFloats=" + pendingFloats + ", productionClock="
                    + productionClock.state() + ", samples=" + samples;
            return new TrackResult(ok, details);
        } finally {
            try { track.pause(); track.flush(); } catch (RuntimeException ignored) { }
            track.release();
        }
    }

    private static String rateText(double rate) { return Double.isFinite(rate) ? Long.toString(Math.round(rate)) : "unavailable"; }

    private static String decoderName() {
        return AudioPlayer.decoderName();
    }

    private static String decode(Properties fixture, String name, int frames, boolean decoderSpecific) throws Exception {
        if (Integer.parseInt(fixture.getProperty("frames" + frames)) != frames) throw new IllegalStateException("bad fixture");
        byte[] cookie = Base64.decode(fixture.getProperty("cookie" + frames), Base64.DEFAULT);
        byte[] csd = decoderSpecific ? EsdsDecoderSpecificInfo.extract(cookie) : cookie;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(csd));
        MediaCodec codec;
        try { codec = MediaCodec.createByCodecName(name); }
        catch (Exception | Error e) { throw new IllegalStateException("AAC create decoder=" + name, e); }
        Throwable primary = null;
        String stage = "configure";
        boolean started = false;
        int input = 0, output = 0, samples = 0;
        try {
            codec.configure(format, null, null, 0);
            stage = "start";
            codec.start(); started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int encodingBytes = 2;
            boolean eosQueued = false, eosSeen = false, aligned = true;
            long firstPts = -1, lastPts = -1, deadline = SystemClock.elapsedRealtime() + 8000;
            while (!eosSeen && SystemClock.elapsedRealtime() < deadline) {
                if (!eosQueued) {
                    stage = "dequeue-input";
                    int index = codec.dequeueInputBuffer(1000);
                    if (index >= 0) {
                        stage = "get-input";
                        ByteBuffer target = codec.getInputBuffer(index);
                        if (target == null) throw new IllegalStateException("missing codec input");
                        target.clear();
                        if (input == 24) {
                            stage = "queue-eos";
                            codec.queueInputBuffer(index, 0, 0, input * (long) frames * 1_000_000 / 48000,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            eosQueued = true;
                        } else {
                            byte[] packet = Base64.decode(fixture.getProperty("packet" + frames + "." + input), Base64.DEFAULT);
                            if (packet.length > target.remaining()) throw new IllegalStateException("codec input too small");
                            target.put(packet);
                            stage = "queue-input";
                            codec.queueInputBuffer(index, 0, packet.length, input * (long) frames * 1_000_000 / 48000, 0);
                            input++;
                        }
                    }
                }
                stage = "dequeue-output";
                int index = codec.dequeueOutputBuffer(info, 1000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    stage = "output-format";
                    MediaFormat out = codec.getOutputFormat();
                    if (out.getInteger(MediaFormat.KEY_SAMPLE_RATE) != 48000 || out.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != 2)
                        throw new IllegalStateException("unexpected PCM format");
                    int encoding = out.containsKey(MediaFormat.KEY_PCM_ENCODING) ? out.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) encodingBytes = 4;
                    else if (encoding != AudioFormat.ENCODING_PCM_16BIT) throw new IllegalStateException("unexpected PCM encoding");
                } else if (index >= 0) {
                    Throwable outputFailure = null;
                    try {
                        stage = "consume-output";
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (info.size % (2 * encodingBytes) != 0) throw new IllegalStateException("partial PCM frame");
                            int count = info.size / (2 * encodingBytes);
                            if (firstPts < 0) firstPts = info.presentationTimeUs;
                            if (lastPts >= 0 && info.presentationTimeUs < lastPts) aligned = false;
                            long expected = firstPts + samples * 1_000_000L / 48000;
                            if (Math.abs(info.presentationTimeUs - expected) > 25) aligned = false;
                            lastPts = info.presentationTimeUs; samples += count; output++;
                        }
                        eosSeen = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } catch (Exception | Error e) {
                        outputFailure = e;
                        throw e;
                    } finally {
                        if (outputFailure == null) stage = "release-output";
                        AudioProbeChecks.cleanup(outputFailure, () -> codec.releaseOutputBuffer(index, false));
                    }
                }
            }
            if (!eosSeen || output == 0 || samples == 0) throw new IllegalStateException("AAC decode produced no complete stream");
            stage = "verify-pts";
            AudioProbeChecks.requirePcmContinuity(aligned, output);
            return "decoder=" + name + ", csdBytes=" + csd.length + ", " + frames + " frames/packet: " + samples + " PCM frames, " + output + " buffers, contiguous PTS=" + aligned;
        } catch (Exception | Error e) {
            IllegalStateException annotated = new IllegalStateException("AAC decoder=" + name + ", csdBytes=" + csd.length + ", stage=" + stage
                    + ", input=" + input + ", output=" + output + ", frames=" + samples, e);
            primary = annotated;
            throw annotated;
        } finally {
            boolean wasStarted = started;
            AudioProbeChecks.cleanup(primary, () -> { if (wasStarted) codec.stop(); }, codec::release);
        }
    }
}
