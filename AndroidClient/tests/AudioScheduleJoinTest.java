package io.github.n2ray.sall;

import java.util.ArrayList;
import java.util.List;

public final class AudioScheduleJoinTest {
    private static AudioDecodeMapping.Decoded pcm(int id) {
        return new AudioDecodeMapping.Decoded(null, id, 0, new float[]{1,2,3,4}, new float[]{-1,-2,-3,-4});
    }
    private static AudioPlayout.Schedule part(int id, AudioPlayout.Part part, int offset, int count, int start) {
        return new AudioPlayout.Schedule(id, part, offset, count, (double) start, start, count,
                0, false, false, 0, 0, 0, null, 0);
    }
    private static AudioPlayout.Output output(AudioPlayout.Schedule... schedules) {
        var out = new AudioPlayout.Output(); out.schedules.addAll(List.of(schedules)); return out;
    }
    public static void main(String[] args) {
        var join = new AudioScheduleJoin(4, .5);
        var timeline = new AudioOutputTimeline(32);
        assert join.admit(0, 1) == AudioScheduleJoin.State.READY;
        assert join.admit(0, 2) == AudioScheduleJoin.State.READY;
        join.apply(0, output(part(1, AudioPlayout.Part.BODY, 0, 2, 0)), 0);
        join.apply(0, output(part(1, AudioPlayout.Part.TAIL, 2, 2, 2),
                part(2, AudioPlayout.Part.BODY, 0, 2, 4)), .01);
        join.decoded(0, pcm(2));
        assert join.pump(0, timeline, .02) == AudioScheduleJoin.State.WAITING;
        assert timeline.endFrame() == 0; // later ready PCM must never overtake.
        join.decoded(0, pcm(1));
        assert join.pump(0, timeline, .03) == AudioScheduleJoin.State.READY;
        assert join.retainedPackets() == 1 && timeline.endFrame() == 6;
        join.apply(0, output(part(2, AudioPlayout.Part.TAIL, 2, 2, 6)), .04);
        assert join.pump(0, timeline, .04) == AudioScheduleJoin.State.READY;
        var samples = new ArrayList<Float>();
        timeline.drain(0, (a, offset, count) -> {
            int n = Math.min(count, 3);
            for (int i = 0; i < n; i++) samples.add(a[offset + i]);
            return n;
        }, () -> false, 32);
        assert samples.equals(List.of(1f,-1f,2f,-2f,3f,-3f,4f,-4f,1f,-1f,2f,-2f,3f,-3f,4f,-4f));
        assert join.retainedPackets() == 0;
        join.admit(0, 3);
        join.apply(0, output(part(3, AudioPlayout.Part.TAIL, 0, 4, 8)), 1);
        var discard = new AudioPlayout.Output(); discard.discards.add(3);
        join.apply(0, discard, 1.1); join.decoded(0, pcm(3));
        assert join.retainedPackets() == 0 && join.pendingSchedules() == 0;
        join.admit(0, 4); join.apply(0, output(part(4, AudioPlayout.Part.TAIL, 0, 4, 8)), 2);
        assert join.pump(0, timeline, 2.5) == AudioScheduleJoin.State.FAILED;
        assert join.failure().contains("timeout");
        join.reset(); timeline.flushed();
        assert join.decoded(0, pcm(4)) == AudioScheduleJoin.State.STALE;
        assert join.apply(0, output(), 3) == AudioScheduleJoin.State.STALE;
        assert join.pump(1, timeline, 3) == AudioScheduleJoin.State.READY;
        var bounded = new AudioScheduleJoin(1, .5);
        assert bounded.admit(0, 1) == AudioScheduleJoin.State.READY;
        assert bounded.admit(0, 2) == AudioScheduleJoin.State.FAILED;
        bounded.reset();
        bounded.admit(1, 1); bounded.decoded(1, pcm(1));
        assert bounded.decoded(1, pcm(1)) == AudioScheduleJoin.State.FAILED;
        bounded.reset(); bounded.admit(2, 1); bounded.decoded(2, pcm(1));
        bounded.apply(2, output(part(1, AudioPlayout.Part.TAIL, 0, 4, 1000)), 4);
        assert bounded.pump(2, new AudioOutputTimeline(2), 4) == AudioScheduleJoin.State.FAILED;
        System.out.println("PASS delayed PCM + deadline tail, ordered join, odd writes, discard, timeout, generation");

        // Real strategy and real decode mapping: tail deadline can occur before codec output.
        var model = new AudioPlayout(); model.format(1, 480, 0, new byte[]{1});
        model.played(0, 10, 10);
        var mapping = new AudioDecodeMapping();
        var bridge = new AudioScheduleJoin(100, .5);
        var sink = new AudioOutputTimeline(48000);
        var packet = new AudioDecodeMapping.Packet(1, 0, 1000, true, 480, new byte[]{1});
        var plan = model.packet(1, 0, 1000, true, 10, 10);
        assert plan.id != null;
        bridge.admit(0, plan.id); bridge.apply(0, plan.output, 10);
        var input = mapping.queue(packet, plan);
        assert model.deadline() != null;
        double deadline = model.deadline();
        bridge.apply(0, model.tick(deadline), deadline);
        assert bridge.pendingSchedules() == 2;
        assert bridge.pump(0, sink, deadline) == AudioScheduleJoin.State.WAITING;
        var bytes = java.nio.ByteBuffer.allocate(480 * 4);
        var result = mapping.output(input.generation(), input.presentationUs(), bytes, 0, bytes.capacity(), AudioDecodeMapping.Encoding.S16_LE);
        assert result.failure() == null && result.decoded().size() == 1;
        bridge.decoded(0, result.decoded().get(0));
        assert bridge.pump(0, sink, deadline + .001) == AudioScheduleJoin.State.READY;
        assert bridge.retainedPackets() == 0 && bridge.pendingSchedules() == 0;
        long bodyStart = Math.round(plan.output.schedules.get(0).start());
        assert sink.endFrame() == bodyStart + 480;
        System.out.println("PASS real AudioPlayout -> AudioDecodeMapping -> asynchronous join -> AudioOutputTimeline, all 480 frames including tail");
    }
}
