package io.github.n2ray.sill;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public final class AudioPlayoutTrace {
    static String output(AudioPlayout.Output o) {
        List<String> p = new ArrayList<>(); p.add("" + o.schedules.size());
        for (var s : o.schedules) {
            for (Object v : new Object[] {s.id(), s.part(), s.offset(), s.count(), s.at(), s.start(), s.frames(), s.change(),
                    s.fadeIn(), s.fadeOut(), s.stamp(), s.seq(), s.epoch(), s.decision().due(), s.decision().ear(), s.decision().picture(), s.goesOut()}) p.add("" + v);
        }
        p.add("" + o.discards.size()); for (int id : o.discards) p.add("" + id);
        return String.join(" ", p);
    }
    public static void main(String[] args) throws Exception {
        AudioPlayout m = new AudioPlayout();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] p = line.split(" "); String result = "ok";
            switch (p[0]) {
                case "change" -> {
                    float[][] changed = AudioPlayout.change(floats(p[2]), floats(p[3]), i(p, 1));
                    result = changed[0].length + " " + values(changed[0]) + " " + changed[1].length + " " + values(changed[1]);
                }
                case "fade" -> {
                    float[] x = floats(p[3]); AudioPlayout.fade(x, i(p, 1) != 0, i(p, 2)); result = values(x);
                }
                case "new" -> m = new AudioPlayout();
                case "format" -> result = "" + m.format(i(p, 1), i(p, 2), i(p, 3), new byte[] {(byte) i(p, 4)});
                case "frame" -> m.frame(d(p, 1), d(p, 2));
                case "latency" -> m.latency(d(p, 1), d(p, 2), d(p, 3), d(p, 4));
                case "rtt" -> m.rtt(p[1].equals("null") ? null : d(p, 1));
                case "away" -> m.away(i(p, 1) != 0);
                case "mute" -> m.mute(i(p, 1) != 0);
                case "played" -> result = output(m.played(d(p, 1), d(p, 2), d(p, 3)));
                case "packet" -> {
                    var a = m.packet(i(p, 1), Long.parseLong(p[2]), d(p, 3), i(p, 4) != 0, d(p, 5), d(p, 6));
                    result = a.decode + " " + a.resetFirst + " " + a.dropFront + " " + a.id + " " + output(a.output);
                }
                case "failed" -> m.decodeFailed();
                case "tick" -> result = output(m.tick(d(p, 1)));
                case "reset" -> result = output(m.reset(AudioPlayout.Reset.valueOf(p[1])));
                case "stats" -> {
                    var s = m.takeSecond(d(p, 1));
                    result = s.behindMs + " " + s.placed + " " + s.late + " " + s.duplicates + " " + s.framesAdded + " " + s.framesDropped + " " + s.jumps + " " + s.joins + " " + s.jumpDrops + " " + s.need + " " + s.cover + " " + s.delay + " " + s.lag + " " + s.error;
                }
                case "jump" -> { var j = m.takeJump(); result = j == null ? "null" : j.by() + " " + j.cause(); }
                case "read" -> result = m.floor() + " " + m.cover(d(p, 1)) + " " + m.need(d(p, 1)) + " " + m.pictureLag(d(p, 1)) + " " + m.deadline() + " " + m.holding();
                default -> throw new IllegalArgumentException(line);
            }
            System.out.println(result);
        }
    }
    static float[] floats(String text) {
        String[] parts = text.split(","); float[] values = new float[parts.length];
        for (int k = 0; k < parts.length; k++) values[k] = Float.parseFloat(parts[k]);
        return values;
    }
    static String values(float[] values) {
        List<String> text = new ArrayList<>(); for (float f : values) text.add("" + (double) f);
        return String.join(" ", text);
    }
    static double d(String[] p, int i) { return Double.parseDouble(p[i]); }
    static int i(String[] p, int i) { return Integer.parseInt(p[i]); }
}
