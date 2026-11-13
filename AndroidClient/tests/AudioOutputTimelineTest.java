package io.github.n2ray.sall;

import java.util.ArrayList;
import java.util.Arrays;

public final class AudioOutputTimelineTest {
    static final class Fake implements AudioOutputTimeline.Sink {
        final ArrayList<Float> samples = new ArrayList<>();
        int limit = Integer.MAX_VALUE, calls;
        public int write(float[] pcm, int offset, int count) {
            calls++;
            int n = Math.min(limit, count);
            for (int i = 0; i < n; i++) samples.add(pcm[offset + i]);
            return n;
        }
    }
    public static void main(String[] args) {
        var q = new AudioOutputTimeline(8);
        var sink = new Fake();
        assert q.enqueue(0, 2, new float[]{1,2}, new float[]{-1,-2});
        assert !q.enqueue(0, 3, new float[]{3}, new float[]{4});
        assert !q.enqueue(0, Long.MAX_VALUE, new float[]{3}, new float[]{4});
        assert !q.enqueue(0, 8, new float[]{3}, new float[]{4});
        assert q.endFrame() == 4 && q.pendingFloats() == 8;
        sink.limit = 1;
        assert q.drain(0, sink, () -> false, 1) == AudioOutputTimeline.Drain.MORE;
        assert q.writtenFrames() == 0 && q.pendingFloats() == 7;
        sink.limit = 0;
        assert q.drain(0, sink, () -> false, 100) == AudioOutputTimeline.Drain.BLOCKED;
        assert sink.calls == 2;
        assert q.drain(0, sink, () -> true, 100) == AudioOutputTimeline.Drain.CANCELLED;
        assert sink.calls == 2;
        sink.limit = 3;
        assert q.drain(0, sink, () -> false, 8) == AudioOutputTimeline.Drain.EMPTY;
        assert sink.samples.equals(Arrays.asList(0f,0f,0f,0f,1f,-1f,2f,-2f));
        assert q.writtenFrames() == 4;
        assert q.enqueue(0, 4, new float[]{8}, new float[]{9});
        long g = q.flushed();
        assert g == 1 && q.endFrame() == 0 && q.pendingFloats() == 0;
        assert q.drain(0, sink, () -> false, 1) == AudioOutputTimeline.Drain.STALE;
        assert !q.enqueue(0, 0, new float[]{8}, new float[]{9});
        assert q.enqueue(g, 0, new float[]{8}, new float[]{9});
        assert q.drain(g, (a,b,c) -> -6, () -> false, 1) == AudioOutputTimeline.Drain.ERROR;
        assert !q.enqueue(g, 1, new float[]{8}, new float[]{9});
        q.flushed();
        assert q.enqueue(2, 0, new float[]{8}, new float[]{9});
        assert q.drain(2, (a,b,c) -> c + 1, () -> false, 1) == AudioOutputTimeline.Drain.ERROR;
        System.out.println("PASS absolute gaps, stereo short/odd/zero writes, cancellation, bounds, errors, flush generation");

        var bounded = new AudioOutputTimeline(2);
        var partial = new Fake(); partial.limit = 1;
        assert bounded.enqueue(0, 0, new float[]{1,2}, new float[]{-1,-2});
        assert bounded.drain(0, partial, () -> partial.calls >= 1, 10) == AudioOutputTimeline.Drain.CANCELLED;
        assert partial.calls == 1 && partial.samples.equals(Arrays.asList(1f));
        assert bounded.pendingFloats() == 3 && bounded.writtenFrames() == 0;
        assert !bounded.enqueue(0, 2, new float[]{3}, new float[]{-3});
        assert bounded.endFrame() == 2 && bounded.pendingFloats() == 3;
        assert bounded.drain(0, partial, () -> false, 1) == AudioOutputTimeline.Drain.MORE;
        assert bounded.pendingFloats() == 2 && bounded.writtenFrames() == 1;
        assert bounded.enqueue(0, 2, new float[]{3}, new float[]{-3});
        assert bounded.pendingFloats() == 4;
        assert bounded.drain(0, partial, () -> false, 10) == AudioOutputTimeline.Drain.EMPTY;
        assert partial.samples.equals(Arrays.asList(1f,-1f,2f,-2f,3f,-3f));
        System.out.println("PASS cancellation after first short write; half-frame capacity rounds up and exact resume preserves stereo");

        var h = new AudioOutputTimeline.Head();
        h.flushed(7);
        assert h.read(6, 0, 0, 0, 0) == null;
        assert h.read(7, -16, 0, 0, 0x100000100L).frame() == 0xfffffff0L;
        assert h.read(7, 16, 1_000_000, 1_000_000, 0x100000100L).frame() == 0x100000010L;
        assert h.read(7, 16, 1_000_000, 300_000_000, 0x100000100L) == null;
        assert h.read(7, 0, 2_000_000, 2_000_000, 0x100000100L) == null;
        assert h.read(7, 17, 3_000_000, 3_000_000, 0x100000100L) == null;
        h.flushed(8);
        assert h.read(8, 0, 4_000_000, 4_000_000, 0).frame() == 0;
        assert h.read(8, 1, 5_000_000, 5_000_000, 0) == null;
        h.flushed(9);
        assert h.read(9, 0, 10, 10, 0) != null;
        assert h.read(9, 0, 9, 10, 0) == null;
        System.out.println("PASS head wrap, stale reading, discontinuity latch, backwards clock, flush reset, write boundary");

        var m = new AudioPlayout();
        m.format(1, 480, 0, new byte[0]); m.played(0, 100, 100);
        var plan = m.packet(1, 0, 99.99, true, 100);
        assert plan.output.schedules.size() == 1;
        var body = plan.output.schedules.get(0);
        assert body.part() == AudioPlayout.Part.BODY && body.count() == 240;
        q = new AudioOutputTimeline(48000);
        float[] pcm = new float[480]; Arrays.fill(pcm, 1);
        assert q.enqueue(0, Math.round(body.start()), Arrays.copyOf(pcm, 240), Arrays.copyOf(pcm, 240));
        var out = new Fake();
        assert q.drain(0, out, () -> false, 10) == AudioOutputTimeline.Drain.EMPTY;
        assert q.endFrame() == Math.round(body.start()) + 240;
        assert m.tick(m.deadline() - .001).schedules.isEmpty();
        var tail = m.tick(m.deadline()).schedules.get(0);
        assert tail.part() == AudioPlayout.Part.TAIL && tail.fadeOut();
        float[] t = Arrays.copyOfRange(pcm, 240, 480);
        AudioPlayout.fade(t, false, 240);
        assert q.enqueue(0, Math.round(tail.start()), t, t);
        assert q.drain(0, out, () -> false, 10) == AudioOutputTimeline.Drain.EMPTY;
        assert out.samples.get(out.samples.size() - 1) > 0 && out.samples.get(out.samples.size() - 1) < .0001f;
        assert q.endFrame() == Math.round(body.start()) + 480;
        m = new AudioPlayout();
        m.format(1, 480, 0, new byte[0]); m.played(0, 100, 100);
        m.packet(1, 0, 99.99, true, 100);
        var continuation = m.packet(1, 1, 100, false, 100.01).output;
        assert continuation.schedules.stream().anyMatch(s -> s.seq() == 0
                && s.part() == AudioPlayout.Part.TAIL && !s.fadeOut());
        System.out.println("PASS model keeps editable 5ms tail outside sink until deadline, then emits faded tail at exact position");
    }
}
