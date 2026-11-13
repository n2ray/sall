package io.github.n2ray.sill;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Opt-in experimental AAC-ELD output. Audio failures never terminate the video connection. */
final class AudioPlayer implements AutoCloseable {
    private interface Event { void apply() throws Exception; }
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sill-audio"); t.setDaemon(true); return t;
    });
    private final AudioRuntime runtime = new AudioRuntime(new AndroidAudioDevice());
    private final java.util.function.Consumer<String> onError;
    // Mailbox controls are coalesced under this lock, never dropped with audio data.
    private boolean closed, requestedEnabled = true, overflow;
    // Serial owner only.
    private boolean enabled = true, blocked;
    private long lastDiagnosticNanos;
    private record Rtt(double seconds, long at) {}
    private final ArrayDeque<Rtt> rtts = new ArrayDeque<>();

    AudioPlayer(java.util.function.Consumer<String> onError) {
        this.onError = onError;
        worker.scheduleWithFixedDelay(this::tick, 0, 5, TimeUnit.MILLISECONDS);
    }
    static boolean supported() { return decoderName() != null; }
    static String decoderName() {
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
    synchronized void receive(byte[] data, double hostStamp, long arrivalNanos) {
        if (closed) return;
        if (data.length == 0 || data.length > 16384) { overflow = true; return; }
        byte[] copy = data.clone();
        post(() -> {
            if (copy[0] == 1) configure(copy);
            else if (copy[0] == 2 && !blocked) runtime.packet(AudioPackets.parse(copy), hostStamp, arrivalNanos, System.nanoTime());
        });
    }
    synchronized void videoFrame(double hostStamp, long arrivalNanos) {
        if (!closed) post(() -> runtime.frame(hostStamp, arrivalNanos));
    }
    synchronized void setPlaybackEnabled(boolean on) { if (!closed) requestedEnabled = on; }
    synchronized void setRemote(boolean remote) { if (!closed) post(() -> runtime.away(remote)); }
    synchronized void timing(double refreshSeconds, double fps) { if (!closed) post(() -> runtime.timing(refreshSeconds, fps)); }
    synchronized void rttSample(double seconds, long arrivalNanos) {
        if (closed || !Double.isFinite(seconds) || seconds < 0 || seconds > 60) return;
        post(() -> {
            while (!rtts.isEmpty() && arrivalNanos - rtts.peek().at() > 1_000_000_000L) rtts.remove();
            if (rtts.size() == 256) rtts.remove();
            rtts.add(new Rtt(seconds, arrivalNanos));
            double[] values = rtts.stream().mapToDouble(Rtt::seconds).sorted().toArray();
            int middle = values.length / 2;
            runtime.rtt(values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2);
        });
    }
    private void post(Event event) {
        if (events.size() >= 256) { overflow = true; return; }
        events.add(event);
    }
    private void tick() {
        try {
            boolean stop, on, lost;
            synchronized (this) {
                stop = closed; on = requestedEnabled; lost = overflow; overflow = false;
                if (stop || lost) events.clear();
            }
            if (stop) { runtime.close(); worker.shutdown(); return; }
            if (lost) throw new IllegalStateException("audio event queue overflow");
            if (on != enabled) {
                enabled = on; runtime.enabled(on);
                if (on) blocked = false;
            }
            for (int i = 0; i < 32; i++) {
                Event event;
                synchronized (this) { event = events.poll(); }
                if (event == null) break;
                event.apply();
            }
            if (!blocked) {
                long now = System.nanoTime();
                if (!rtts.isEmpty() && now - rtts.peekLast().at() > 1_000_000_000L) {
                    rtts.clear(); runtime.rtt(null);
                }
                runtime.tick(now);
                if (now - lastDiagnosticNanos >= 1_000_000_000L) {
                    lastDiagnosticNanos = now;
                    String diagnostic = runtime.diagnostics();
                    if (diagnostic != null) android.util.Log.i("SillAndroid", diagnostic);
                }
            }
        } catch (Exception e) {
            boolean first = !blocked; blocked = true;
            try { runtime.close(); } catch (RuntimeException ignored) { }
            synchronized (this) { events.clear(); }
            if (first) {
                android.util.Log.w("SillAndroid", "Experimental audio stopped; video remains active", e);
                try { onError.accept("实验声音已停止（解码或设备时钟不可用），画面仍可使用"); }
                catch (RuntimeException ignored) { }
            }
        }
    }
    private void configure(byte[] data) throws Exception {
        if (data.length > 4096) throw new IllegalArgumentException("Audio format too large");
        JSONObject f = new JSONObject(new String(data, 1, data.length - 1, StandardCharsets.UTF_8));
        if (!"aac-eld".equals(f.optString("codec")) || f.optInt("sampleRate") != 48000 || f.optInt("channels") != 2)
            throw new IllegalArgumentException("Unsupported audio format");
        int frames = f.optInt("framesPerPacket"), priming = f.optInt("primingFrames"), epoch = f.optInt("epoch", -1);
        if (frames < 1 || frames > 8192 || priming < 0 || priming > 8192 || epoch < 0 || epoch > 65535)
            throw new IllegalArgumentException("Invalid audio format");
        byte[] cookie = Base64.decode(f.optString("cookie", ""), Base64.DEFAULT);
        if (cookie.length == 0 || cookie.length > 1024) throw new IllegalArgumentException("Invalid audio configuration");
        runtime.format(epoch, frames, priming, cookie); blocked = false;
    }
    @Override public synchronized void close() {
        closed = true; events.clear();
    }
}
