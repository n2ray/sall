package io.github.n2ray.sall;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Serial-owner rendezvous for asynchronous PCM and model decisions. No Android/device clock here.
 * Register an admitted plan before applying its output; decoder flush alone must not reset this
 * bridge, since the preceding packet's tail can still be scheduled after the decoder resets.
 * A physical output reset invalidates the whole bridge and its timeline together.
 */
final class AudioScheduleJoin {
    enum State { READY, WAITING, STALE, FAILED }
    private static final class Kept {
        float[] left, right;
    }
    private record Waiting(AudioPlayout.Schedule schedule, double since) {}
    private final Map<Integer, Kept> kept = new HashMap<>();
    private final ArrayDeque<Waiting> schedules = new ArrayDeque<>();
    private final int capacity;
    private final double timeout;
    private long generation;
    private String failure;

    AudioScheduleJoin(int capacity, double timeoutSeconds) {
        if (capacity < 1 || capacity > 1000 || !Double.isFinite(timeoutSeconds) || timeoutSeconds <= 0)
            throw new IllegalArgumentException();
        this.capacity = capacity; timeout = timeoutSeconds;
    }
    long generation() { return generation; }
    int retainedPackets() { return kept.size(); }
    int pendingSchedules() { return schedules.size(); }
    String failure() { return failure; }

    State admit(long token, Integer id) {
        if (token != generation) return State.STALE;
        if (failure != null) return State.FAILED;
        if (id == null) return State.READY;
        if (kept.containsKey(id)) return fail("duplicate admitted id");
        if (kept.size() >= capacity) return fail("PCM retention capacity");
        kept.put(id, new Kept());
        return State.READY;
    }
    State decoded(long token, AudioDecodeMapping.Decoded decoded) {
        if (token != generation) return State.STALE;
        if (failure != null) return State.FAILED;
        Kept pcm = kept.get(decoded.id());
        // A discard may have overtaken a decoder callback; never resurrect it.
        if (pcm == null) return State.READY;
        if (pcm.left != null) return fail("duplicate decoded id");
        if (decoded.left().length != decoded.right().length || decoded.left().length == 0)
            return fail("invalid PCM shape");
        pcm.left = decoded.left().clone(); pcm.right = decoded.right().clone();
        return State.READY;
    }
    State apply(long token, AudioPlayout.Output output, double now) {
        if (token != generation) return State.STALE;
        if (failure != null) return State.FAILED;
        if (!Double.isFinite(now)) return fail("invalid monotonic time");
        for (int id : output.discards) {
            kept.remove(id);
            schedules.removeIf(w -> w.schedule.id() == id);
        }
        for (var schedule : output.schedules) {
            if (!kept.containsKey(schedule.id())) return fail("schedule without admission");
            if (schedules.size() >= capacity * 2) return fail("schedule capacity");
            schedules.add(new Waiting(schedule, now));
        }
        return schedules.isEmpty() ? State.READY : State.WAITING;
    }
    State pump(long token, AudioOutputTimeline timeline, double now) {
        if (token != generation) return State.STALE;
        if (failure != null) return State.FAILED;
        if (!Double.isFinite(now)) return fail("invalid monotonic time");
        while (!schedules.isEmpty()) {
            Waiting waiting = schedules.peek();
            var s = waiting.schedule;
            Kept pcm = kept.get(s.id());
            if (pcm == null) return fail("schedule lost PCM ownership");
            if (pcm.left == null) {
                if (now - waiting.since >= timeout) return fail("decoder rendezvous timeout");
                return State.WAITING;
            }
            if (s.offset() < 0 || s.count() <= 0 || s.offset() > pcm.left.length - s.count()
                    || !Double.isFinite(s.start()) || s.start() < 0 || s.start() > Long.MAX_VALUE / 2.0)
                return fail("invalid schedule range");
            float[] left = Arrays.copyOfRange(pcm.left, s.offset(), s.offset() + s.count());
            float[] right = Arrays.copyOfRange(pcm.right, s.offset(), s.offset() + s.count());
            if (s.change() != 0) {
                float[][] changed = AudioPlayout.change(left, right, s.change());
                left = changed[0]; right = changed[1];
            }
            if (left.length != s.frames()) return fail("rendered frame count mismatch");
            if (s.fadeIn()) { AudioPlayout.fade(left, true, AudioPlayout.FADE_FRAMES); AudioPlayout.fade(right, true, AudioPlayout.FADE_FRAMES); }
            if (s.fadeOut()) { AudioPlayout.fade(left, false, AudioPlayout.FADE_FRAMES); AudioPlayout.fade(right, false, AudioPlayout.FADE_FRAMES); }
            if (!timeline.enqueue(timeline.generation(), Math.round(s.start()), left, right))
                return fail("output timeline rejected schedule");
            schedules.remove();
            if (s.part() == AudioPlayout.Part.TAIL) kept.remove(s.id());
        }
        return State.READY;
    }
    long reset() {
        generation = Math.incrementExact(generation);
        kept.clear(); schedules.clear(); failure = null;
        return generation;
    }
    private State fail(String reason) { failure = reason; return State.FAILED; }
}
