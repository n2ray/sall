package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Three- and four-finger stroke rules from iOS TrackpadGestures, in dp and milliseconds. */
final class MultiFingerGesture {
    record Decision(String gesture, int fingers) { }
    private record Point(double x, double y) { }
    private record Sample(long time, Point point) { }
    private static final class Finger {
        final Point landed; final long time; Point now;
        Finger(Point p, long t) { landed = now = p; time = t; }
    }
    private final Map<Integer, Finger> down = new HashMap<>();
    private final Map<Integer, Point> armed = new HashMap<>(), others = new HashMap<>(), otherNow = new HashMap<>();
    private final List<Sample> samples = new ArrayList<>();
    private boolean silent, travelled, done;

    boolean down(int id, double x, double y, long time, boolean holding) {
        if (down.isEmpty()) { reset(); }
        Point p = new Point(x, y); down.put(id, new Finger(p, time));
        if (down.size() >= 5) done = true;
        if (!armed.isEmpty()) { others.put(id, p); otherNow.put(id, p); return false; }
        if (down.size() < 3 || travelled || holding) return false;
        boolean justSilenced = !silent; silent = true;
        List<Map.Entry<Integer, Finger>> ordered = new ArrayList<>(down.entrySet());
        ordered.sort(Comparator.<Map.Entry<Integer, Finger>>comparingLong(e -> e.getValue().time).thenComparingInt(Map.Entry::getKey));
        List<Map.Entry<Integer, Finger>> last = ordered.subList(ordered.size() - 3, ordered.size());
        if (!done && time - last.get(0).getValue().time <= 150) {
            for (var item : last) armed.put(item.getKey(), item.getValue().now);
            for (var item : down.entrySet()) if (!armed.containsKey(item.getKey())) {
                others.put(item.getKey(), item.getValue().now); otherNow.put(item.getKey(), item.getValue().now);
            }
            samples.add(new Sample(time, centroid(armed)));
        }
        return justSilenced;
    }
    void move(int id, double x, double y, long time) {
        Finger finger = down.get(id); if (finger == null) return;
        finger.now = new Point(x, y);
        if (armed.isEmpty()) { if (distance(finger.landed, finger.now) >= 24) travelled = true; return; }
        if (others.containsKey(id)) otherNow.put(id, finger.now);
        if (!done && armed.containsKey(id)) {
            samples.add(new Sample(time, centroid(nowArmed())));
            // Only the most recent 50 ms and the sample just before it can affect flick speed.
            while (samples.size() > 2 && samples.get(1).time <= time - 50) samples.remove(0);
        }
    }
    Decision up(int id, double x, double y, long time) {
        if (!down.containsKey(id)) return null;
        move(id, x, y, time);
        Decision decision = null;
        if (!done && armed.containsKey(id)) { done = true; decision = decide(time); }
        down.remove(id); return decision;
    }
    void cancel() { reset(); }
    private void reset() {
        down.clear(); armed.clear(); others.clear(); otherNow.clear(); samples.clear();
        silent = travelled = done = false;
    }
    private Map<Integer, Point> nowArmed() {
        Map<Integer, Point> points = new HashMap<>();
        for (var entry : armed.entrySet()) {
            Finger f = down.get(entry.getKey()); points.put(entry.getKey(), f == null ? entry.getValue() : f.now);
        }
        return points;
    }
    private Decision decide(long time) {
        Map<Integer, Point> now = nowArmed(); Point start = centroid(armed), end = centroid(now);
        double dx = end.x - start.x, dy = end.y - start.y, travel = Math.hypot(dx, dy);
        Map<Integer, Point> movers = new HashMap<>();
        for (var e : others.entrySet()) if (distance(e.getValue(), otherNow.get(e.getKey())) >= 24) movers.put(e.getKey(), e.getValue());
        int fingers = Math.min(4, 3 + movers.size());
        if (travel >= 40 || (travel >= 20 && speed(end, time) >= 500)) {
            double major = Math.max(Math.abs(dx), Math.abs(dy)), minor = Math.min(Math.abs(dx), Math.abs(dy));
            if (major >= 1.3 * minor) {
                boolean horizontal = Math.abs(dx) > Math.abs(dy);
                double direction = Math.signum(horizontal ? dx : dy), along = horizontal ? Math.abs(dx) : Math.abs(dy);
                boolean together = true;
                for (var e : armed.entrySet()) {
                    Point a = e.getValue(), b = now.get(e.getKey());
                    if (((horizontal ? b.x - a.x : b.y - a.y) * direction) < .5 * along) together = false;
                }
                for (var e : movers.entrySet()) {
                    Point a = e.getValue(), b = otherNow.get(e.getKey());
                    if (((horizontal ? b.x - a.x : b.y - a.y) * direction) <= -.5 * along) together = false;
                }
                if (together) return new Decision(horizontal ? (dx < 0 ? "swipeLeft" : "swipeRight") : (dy < 0 ? "swipeUp" : "swipeDown"), fingers);
            }
        }
        Map<Integer, Point> before = new HashMap<>(armed), after = new HashMap<>(now);
        for (var e : movers.entrySet()) { before.put(e.getKey(), e.getValue()); after.put(e.getKey(), otherNow.get(e.getKey())); }
        double r0 = spread(before), r1 = spread(after);
        if (r0 <= 0) return null;
        if (r1 / r0 <= .75) return new Decision("pinch", fingers);
        if (r1 / r0 >= 1.25) return new Decision("spread", fingers);
        return null;
    }
    private double speed(Point end, long time) {
        if (samples.isEmpty()) return 0;
        Sample from = samples.get(0);
        for (Sample s : samples) if (s.time <= time - 50) from = s;
        return time > from.time ? distance(end, from.point) * 1000 / (time - from.time) : 0;
    }
    private static Point centroid(Map<Integer, Point> points) {
        double x = 0, y = 0;
        for (Point p : points.values()) { x += p.x; y += p.y; }
        return new Point(x / points.size(), y / points.size());
    }
    private static double spread(Map<Integer, Point> points) {
        Point center = centroid(points); double sum = 0;
        for (Point p : points.values()) sum += distance(p, center);
        return sum / points.size();
    }
    private static double distance(Point a, Point b) { return Math.hypot(a.x - b.x, a.y - b.y); }
}
