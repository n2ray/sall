package io.github.n2ray.sall;

import android.media.AudioTrack;
import android.util.Base64;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;

/** Exercises production runtime/device with synthetic timestamps and forced-silent output. */
final class AudioRuntimeProbe {
    private static final int MAX_PACKETS = 160;
    private static final long TICK_NS = 5_000_000L;
    private static final Field TRACK = field(AndroidAudioDevice.class, "track");
    private static final Field CLOCK = field(AudioRuntime.class, "clock");
    private static final Field MODEL = field(AudioRuntime.class, "model");

    private static Field field(Class<?> owner, String name) {
        try { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Probe field unavailable: " + name, e); }
    }

    private static final class SilentDevice implements AudioRuntime.Device {
        final AndroidAudioDevice actual = new AndroidAudioDevice();
        float[] silence = new float[0];
        int offers, pcmBuffers, pcmFrames, writes, timestamps, underruns = -1, bufferFrames = -1;
        long writtenFloats, firstWriteNs = -1, lastFrame = -1, lastStamp = -1, head = -1;

        public void open(byte[] cookie) throws Exception {
            actual.open(cookie);
            AudioTrack track = track();
            if (track == null) throw new IllegalStateException("silent test track missing");
            bufferFrames = track.getBufferSizeInFrames();
            snapshot(track);
        }
        public void resetDecoder() throws Exception { actual.resetDecoder(); }
        public boolean offer(byte[] packet, long ptsUs) throws Exception {
            boolean accepted = actual.offer(packet, ptsUs);
            if (accepted) offers++;
            return accepted;
        }
        public AudioRuntime.PCM poll() throws Exception {
            AudioRuntime.PCM pcm = actual.poll();
            if (pcm != null) {
                int sampleBytes = pcm.encoding() == AudioDecodeMapping.Encoding.FLOAT32_LE ? 4 : 2;
                int bytes = pcm.bytes().remaining();
                if (bytes <= 0 || bytes % (2 * sampleBytes) != 0) throw new IllegalStateException("invalid synthetic PCM frame size");
                pcmBuffers++; pcmFrames += bytes / (2 * sampleBytes);
            }
            return pcm;
        }
        public AudioRuntime.Stamp timestamp() {
            AudioRuntime.Stamp stamp = actual.timestamp();
            if (stamp != null) { timestamps++; lastFrame = stamp.frame(); lastStamp = stamp.nanos(); }
            AudioTrack track = track(); if (track != null) snapshot(track);
            return stamp;
        }
        public int write(float[] ignored, int offset, int count) {
            if (count < 0 || offset < 0 || offset > ignored.length - count) throw new IllegalArgumentException("invalid write range");
            if (silence.length < count) silence = new float[count];
            if (firstWriteNs < 0) firstWriteNs = System.nanoTime();
            int written = actual.write(silence, 0, count);
            if (written > 0) writtenFloats += written;
            writes++;
            return written;
        }
        public int bufferFrames() { return actual.bufferFrames(); }
        public int underruns() { return actual.underruns(); }
        public boolean routeChanged() { return actual.routeChanged(); }
        public void close() {
            try { AudioTrack track = track(); if (track != null) snapshot(track); }
            finally { actual.close(); }
        }
        private AudioTrack track() {
            try { return (AudioTrack) TRACK.get(actual); }
            catch (IllegalAccessException e) { throw new IllegalStateException("Cannot inspect probe track", e); }
        }
        private void snapshot(AudioTrack track) {
            underruns = track.getUnderrunCount();
            head = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
        }
    }

    static AudioProbeChecks.Result run(Properties fixture, int frames, boolean decoderSpecific) {
        String label = "runtime " + frames + (decoderSpecific ? " ASC" : " raw");
        SilentDevice device = null;
        AudioRuntime runtime = null;
        long started = System.nanoTime(), firstPlacementFloats = -1;
        int supplied = 0, tickCount = 0;
        boolean readySeen = false, placedSeen = false;
        String stage = "fixture", failure = null, lastRuntime = "unavailable";
        StringBuilder samples = new StringBuilder();
        try {
            int count = Integer.parseInt(fixture.getProperty("runtimePackets"));
            int fixtureFrames = Integer.parseInt(fixture.getProperty("frames" + frames));
            int prime = Integer.parseInt(fixture.getProperty("priming" + frames));
            if (count != MAX_PACKETS || fixtureFrames != frames || prime < 0 || prime > frames)
                throw new IllegalArgumentException("invalid synthetic format");
            byte[] raw = Base64.decode(fixture.getProperty("cookie" + frames), Base64.DEFAULT);
            byte[] cookie = decoderSpecific ? EsdsDecoderSpecificInfo.extract(raw) : raw;
            byte[][] packets = new byte[count][];
            for (int i = 0; i < count; i++) {
                packets[i] = Base64.decode(fixture.getProperty("packet" + frames + "." + i), Base64.DEFAULT);
                if (packets[i].length == 0 || packets[i].length > 8192) throw new IllegalArgumentException("invalid synthetic packet");
            }

            device = new SilentDevice();
            runtime = new AudioRuntime(device);
            runtime.away(true); runtime.rtt(.04); runtime.timing(1.0 / 60, 60);
            runtime.format(1, frames, prime, cookie);
            long start = System.nanoTime();
            started = start;
            double wall = System.currentTimeMillis() / 1000.0;
            long framePeriod = 1_000_000_000L / 60;
            long nextVideo = start;
            long duration = count * (long) frames * 1_000_000_000L / 48000 + 1_000_000_000L;
            long nextSample = start;
            int sampleCount = 0;
            while (System.nanoTime() - start < duration) {
                long now = System.nanoTime();
                if (now >= nextVideo) {
                    stage = "video";
                    runtime.frame(wall + (now - start) / 1e9, now);
                    nextVideo = now + framePeriod;
                }
                while (supplied < count && now - start >= supplied * (long) frames * 1_000_000_000L / 48000) {
                    stage = "packet";
                    long arrival = start + supplied * (long) frames * 1_000_000_000L / 48000;
                    runtime.packet(new AudioPackets.Batch(1, supplied, supplied == 0, List.of(packets[supplied])),
                            wall + supplied * (double) frames / 48000, arrival, System.nanoTime());
                    supplied++;
                    now = System.nanoTime();
                }
                stage = "tick";
                runtime.tick(now); tickCount++;
                String diagnostics = runtime.diagnostics();
                if (diagnostics != null) lastRuntime = diagnostics;
                AudioPresentationClock clock = (AudioPresentationClock) CLOCK.get(runtime);
                AudioPlayout model = (AudioPlayout) MODEL.get(runtime);
                if (clock != null && clock.state() == AudioPresentationClock.State.READY) readySeen = true;
                if (model.placedThisSession && !placedSeen) { placedSeen = true; firstPlacementFloats = device.writtenFloats; }
                if (now >= nextSample && sampleCount < 20) {
                    if (samples.length() > 0) samples.append('|');
                    samples.append("ms=").append((now - start) / 1_000_000)
                            .append(",clock=").append(clock == null ? "none" : clock.state())
                            .append(",offered=").append(device.offers).append(",pcmFrames=").append(device.pcmFrames)
                            .append(",writtenFrames=").append(device.writtenFloats / 2)
                            .append(",head=").append(device.head).append(",underruns=").append(device.underruns)
                            .append(",placed=").append(model.placedThisSession);
                    if (device.lastStamp > 0) samples.append(",timestampFrame=").append(device.lastFrame)
                            .append(",timestampAgeUs=").append((System.nanoTime() - device.lastStamp) / 1000);
                    sampleCount++; nextSample = now + 100_000_000L;
                }
                Thread.sleep(TICK_NS / 1_000_000);
            }
            AudioTrack finalTrack = device.track(); if (finalTrack != null) device.snapshot(finalTrack);
            if (supplied != count || device.offers != count || device.pcmBuffers == 0 || device.pcmFrames == 0
                    || device.timestamps < 3 || !readySeen || !placedSeen || device.writtenFloats <= firstPlacementFloats
                    || device.underruns != 0) {
                throw new IllegalStateException("runtime did not complete silent playback without underrun");
            }
        } catch (Throwable e) {
            failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (runtime != null) {
                try { String diagnostics = runtime.diagnostics(); if (diagnostics != null) lastRuntime = diagnostics; }
                catch (Throwable ignored) { }
            }
        } finally {
            if (runtime != null) try { runtime.close(); }
            catch (Throwable e) { failure = (failure == null ? "" : failure + "; ") + "cleanup " + e.getClass().getSimpleName() + ": " + e.getMessage(); }
        }
        if (failure == null && device != null && device.underruns != 0) failure = "final AudioTrack underrun count was not zero";
        String detail = label + ": " + (failure == null ? "completed" : "stage=" + stage + ", failure=" + failure)
                + ", elapsedMs=" + ((System.nanoTime() - started) / 1_000_000)
                + ", packets=" + supplied + ", ticks=" + tickCount + ", clockReady=" + readySeen + ", placed=" + placedSeen
                + ", runtime=" + lastRuntime
                + (device == null ? "" : ", offered=" + device.offers + ", pcmBuffers=" + device.pcmBuffers
                + ", pcmFrames=" + device.pcmFrames + ", writes=" + device.writes + ", writtenFrames=" + device.writtenFloats / 2
                + ", firstWriteMs=" + (device.firstWriteNs < 0 ? "none" : (device.firstWriteNs - started) / 1_000_000)
                + ", timestamps=" + device.timestamps + ", bufferFrames=" + device.bufferFrames
                + ", head=" + device.head + ", underruns=" + device.underruns)
                + ", samples=" + samples;
        return new AudioProbeChecks.Result(failure == null ? AudioProbeChecks.Status.PASS : AudioProbeChecks.Status.FAIL, detail);
    }
}
