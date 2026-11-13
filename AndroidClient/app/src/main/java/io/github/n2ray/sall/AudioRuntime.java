package io.github.n2ray.sall;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/** Single serial-owner audio session. Device operations must be nonblocking except open/reset/close.
 * Device timestamps are presentation timestamps, so output latency is not added a second time.
 * This experimental adapter requires codec output PTS to identify its first 48 kHz PCM frame.
 * Any violated contract fails the audio session; callers keep video independent.
 */
final class AudioRuntime implements AutoCloseable {
    record PCM(long ptsUs, ByteBuffer bytes, AudioDecodeMapping.Encoding encoding) {}
    record Stamp(long frame, long nanos) {}
    interface Device extends AutoCloseable {
        void open(byte[] cookie) throws Exception;
        void resetDecoder() throws Exception;
        boolean offer(byte[] packet, long ptsUs) throws Exception;
        PCM poll() throws Exception;
        Stamp timestamp();
        int write(float[] pcm, int offset, int count);
        int bufferFrames();
        default int underruns() { return 0; }
        boolean routeChanged();
        @Override void close();
    }
    private final Device device;
    private final java.util.function.LongSupplier monotonic;
    private final AudioPlayout model = new AudioPlayout();
    private final AudioDecodeMapping mapping = new AudioDecodeMapping();
    private final AudioScheduleJoin join = new AudioScheduleJoin(100, .5);
    private final AudioOutputTimeline timeline = new AudioOutputTimeline(96000);
    private final ArrayDeque<AudioDecodeMapping.Input> inputs = new ArrayDeque<>();
    private AudioPresentationClock clock;
    private AudioStartupSupply startup;
    private boolean handedOff;
    private double outputLeadFrames;
    private byte[] cookie;
    private int epoch = -1, frames;
    private boolean enabled = true, opened, submitted;
    private double lastPacket;
    private double refresh = 1.0 / 60, streamFPS = 60;

    AudioRuntime(Device device) { this(device, System::nanoTime); }
    AudioRuntime(Device device, java.util.function.LongSupplier monotonic) {
        this.device = device; this.monotonic = monotonic; model.away(true);
    }
    static double streamFPS(double reported, int requestedCap) {
        return Double.isFinite(reported) && reported > 0 ? reported : requestedCap > 0 ? requestedCap : 60;
    }
    void format(int epoch, int frames, int prime, byte[] cookie) {
        if (!model.format(epoch, frames, prime, cookie)) return;
        resetPlayback();
        this.epoch = epoch; this.frames = frames; this.cookie = cookie.clone();
    }
    void frame(double stamp, long arrival) { model.frame(stamp, arrival / 1e9); }
    void away(boolean away) { model.away(away); }
    void rtt(Double seconds) { model.rtt(seconds); }
    void timing(double refreshSeconds, double fps) {
        if (!Double.isFinite(refreshSeconds) || refreshSeconds <= 0 || !Double.isFinite(fps) || fps <= 0) return;
        refresh = refreshSeconds; streamFPS = fps;
        updateLatency();
    }
    private void updateLatency() { if (opened) model.latency(0, outputLeadFrames / 48000.0, refresh, streamFPS); }
    String diagnostics() {
        if (!opened) return null;
        return "Audio experimental generation=" + join.generation() + " clock=" + clock.state()
                + " writtenFrames=" + timeline.writtenFrames() + " pendingFloats=" + timeline.pendingFloats()
                + " decoderPending=" + mapping.pendingPackets() + " schedulePending=" + join.pendingSchedules()
                + " startupBudget=" + startup.budget() + " startupAllocated=" + startup.allocated()
                + " handoff=" + handedOff + " writeLeadFrames=" + outputLeadFrames;
    }
    void enabled(boolean on) {
        if (enabled == on) return;
        enabled = on; model.mute(!on); resetPlayback();
    }
    void packet(AudioPackets.Batch batch, double stamp, long arrival, long now) throws Exception {
        if (cookie == null || batch.epoch() != epoch) return;
        lastPacket = now / 1e9;
        if (enabled && !opened) {
            device.open(cookie);
            now = monotonic.getAsLong();
            clock = new AudioPresentationClock(now);
            startup = new AudioStartupSupply(device.bufferFrames(), now);
            opened = true;
            handedOff = false;
            // Buffer capacity is conservative write-ahead, not a claim of measured hardware IO latency.
            outputLeadFrames = Math.max(240, device.bufferFrames()); updateLatency();
        }
        for (var packet : AudioDecodeMapping.expand(batch, stamp, frames)) {
            if (opened) decode();
            var plan = model.packet(packet.epoch(), packet.sequence(), packet.stamp(), packet.segmentStart(), arrival / 1e9, now / 1e9);
            if (plan.resetFirst) {
                // Resetting an asynchronous codec may abandon the preceding, still scheduled tail.
                // Fail explicitly instead of silently dropping it or mislabelling late output.
                require(inputs.isEmpty() && mapping.pendingPackets() == 0, "codec reset with unresolved PCM");
                if (submitted) device.resetDecoder();
                mapping.flush(); submitted = false;
            }
            if (!plan.output.discards.isEmpty()) {
                var discards = new AudioPlayout.Output();
                discards.discards.addAll(plan.output.discards);
                apply(discards, now / 1e9);
                plan.output.discards.clear();
            }
            require(join.admit(join.generation(), plan.id) != AudioScheduleJoin.State.FAILED, join.failure());
            apply(plan.output, now / 1e9);
            var input = mapping.queue(packet, plan);
            if (input != null) inputs.add(input);
        }
    }
    void tick(long now) throws Exception {
        if (!opened) return;
        if (device.routeChanged() || now / 1e9 - lastPacket >= 10) { resetPlayback(); return; }
        decode();
        var stamp = device.timestamp();
        now = monotonic.getAsLong();
        require(device.underruns() == 0, "audio underrun during " + clock.state());
        if (stamp != null) clock.observe(clock.generation(), stamp.frame, stamp.nanos, now, timeline.writtenFrames());
        require(clock.check(now) != AudioPresentationClock.State.FAILED,
                "presentation timestamp unavailable or discontinuous: " + clock.failure());
        if (!handedOff) startup.observe(stamp, now, timeline.writtenFrames());
        var reading = clock.reading();
        if (reading != null && !handedOff) {
            double presentedNow = reading.frame() + (now - reading.nanos()) / 1e9 * 48000;
            // Bootstrap zeros are irrevocably committed. Freeze this generation's writable
            // horizon before the model admits its waiting PCM; never derive it from later music.
            outputLeadFrames = Math.max(outputLeadFrames, timeline.endFrame() - presentedNow);
            handedOff = true; updateLatency();
        }
        if (reading != null) apply(model.played(reading.frame(), reading.nanos() / 1e9, now / 1e9), now / 1e9);
        apply(model.tick(now / 1e9), now / 1e9);
        require(join.pump(join.generation(), timeline, now / 1e9) != AudioScheduleJoin.State.FAILED, join.failure());
        if (clock.state() == AudioPresentationClock.State.STARTING) {
            int count = startup.replenish(now);
            if (count > 0) silence(count);
        } else if (!model.holding() && join.pendingSchedules() == 0) {
            // Keep the hardware clock advancing in gaps, but never commit over an editable tail.
            long target = reading.frame() + Math.max(0, Math.round((now - reading.nanos()) / 1e9 * 48000))
                    + Math.max(960, (long) Math.ceil(outputLeadFrames));
            long gap = target - timeline.endFrame();
            // Fill elapsed demand in one bounded allocation, not a fixed number of frames per
            // invocation. A stalled writer must fail rather than accumulate unbounded silence.
            require(gap <= AudioPresentationClock.STARTUP_FRAMES, "idle audio replenishment exceeds bound");
            if (gap > 0) silence((int) gap);
        }
        var result = timeline.drain(timeline.generation(), device::write, () -> false, 8);
        require(result != AudioOutputTimeline.Drain.ERROR, "AudioTrack write failed");
    }
    private void silence(int frames) {
        require(timeline.enqueue(timeline.generation(), timeline.endFrame(), new float[frames], new float[frames]), "silence capacity");
    }
    private void decode() throws Exception {
        for (int i = 0; i < 16; i++) {
            var pcm = device.poll();
            if (pcm == null) break;
            var result = mapping.output(mapping.generation(), pcm.ptsUs, pcm.bytes, 0, pcm.bytes.remaining(), pcm.encoding);
            require(result.failure() == null, "invalid codec PCM/PTS: " + result.failure());
            for (var decoded : result.decoded())
                require(join.decoded(join.generation(), decoded) != AudioScheduleJoin.State.FAILED, join.failure());
        }
        for (int i = 0; i < 16 && !inputs.isEmpty(); i++) {
            var input = inputs.peek();
            if (!device.offer(input.packet().data(), input.presentationUs())) break;
            submitted = true; inputs.remove();
        }
    }
    private void apply(AudioPlayout.Output output, double now) {
        require(join.apply(join.generation(), output, now) != AudioScheduleJoin.State.FAILED, join.failure());
    }
    private void resetPlayback() {
        device.close(); opened = submitted = false; clock = null; startup = null;
        handedOff = false; outputLeadFrames = 0;
        model.reset(AudioPlayout.Reset.PLAYBACK); mapping.flush(); join.reset(); timeline.flushed(); inputs.clear();
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    @Override public void close() { resetPlayback(); }
}
