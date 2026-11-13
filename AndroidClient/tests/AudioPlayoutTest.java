package io.github.n2ray.sall;

import java.util.Arrays;

/** Numeric and deadline scenarios transcribed from Tests/checks/audio-playout/main.swift. */
public final class AudioPlayoutTest {
    static void near(double a, double b) { assert Math.abs(a - b) < 1e-9 : a + " != " + b; }
    static AudioPlayout model(int frames, int priming) {
        AudioPlayout m = new AudioPlayout();
        assert m.format(1, frames, priming, new byte[0]);
        return m;
    }
    public static void main(String[] args) {
        AudioPlayout m = model(512, 256);
        assert !m.format(1, 512, 256, new byte[0]);
        m.latency(.011, .005, 1.0 / 60, 60);
        assert m.floor() == null;
        near(m.cover(0), .040); m.away(true); near(m.cover(0), .150); m.away(false);
        m.packet(1, 0, 99.995, true, 100);
        near(m.floor(), .005); near(m.need(100), .040 + 512 / 48000.0 + .005 + .011 + .002);
        m.packet(1, 1, 100.5 - .0053, false, 100.6); near(m.cover(100.6), .040);
        m.packet(1, 2, 102.3 - .035, false, 102.3); near(m.cover(102.3), .030);
        m.packet(1, 3, 102.4 - .0051, false, 102.4); near(m.cover(102.4), .030);
        m = model(480, 240);
        m.packet(1, 0, 9.990, true, 10);
        m.packet(1, 1, 12.5 - .011, false, 12.5); near(m.cover(12.5), .020);
        m.packet(1, 2, 12.6 - .310, false, 12.6); near(m.cover(12.6), .150);
        m.away(true); near(m.cover(12.6), .300);
        m.packet(1, 3, 12.7 - .910, false, 12.7); near(m.cover(12.7), .600);
        m = model(480, 240);
        m.packet(1, 0, .990, true, 1);
        for (int k = 1; k <= 9; k++) m.packet(1, k, k + 1.5 - .015, false, k + 1.5);
        near(m.floor(), .010);
        m.packet(1, 10, 11.5 - .015, false, 11.5); near(m.floor(), .015);
        m = model(480, 240);
        m.latency(.010, .005, 1.0 / 60, 60);
        m.packet(1, 0, 9.980, true, 10);
        double[] raws = {.030, .010, .050, .012, .040};
        for (int k = 0; k < raws.length; k++) m.frame(10 + k * .1 - raws[k], 10 + k * .1);
        near(m.pictureLag(10.5), .039);
        for (int k = 0; k < 5; k++) m.frame(12.1 + k * .1 - .300, 12.1 + k * .1);
        near(m.pictureLag(12.5), .309); near(m.pictureLag(20), .309);
        near(m.delay(12.5, m.pictureLag(12.5)), .309 + 1.0 / 60);
        m = model(480, 0); m.latency(.010, .005, 1.0 / 60, 60); m.played(0, 100, 100);
        double t = 100;
        for (int k = 0; k < 50; k++, t += .010) m.packet(1, k, t - .023, k == 0, t);
        double now = m.deadline() + .004;
        AudioPlayout.Output out = m.packet(1, 50, t - .023, false, now).output;
        assert out.schedules.stream().anyMatch(s -> s.seq() == 49 && s.part() == AudioPlayout.Part.TAIL && s.fadeOut());
        for (var s : out.schedules) if (s.seq() == 50) {
            assert s.at() != null && s.fadeIn(); assert s.goesOut() - now >= .007 - 1e-9;
        }
        m = model(480, 240); m.played(0, 10, 10);
        var first = m.packet(1, 0xffffffffL, 9.99, true, 10);
        assert first.decode && first.resetFirst && first.dropFront == 240;
        var next = m.packet(1, 0, 10, false, 10.01);
        assert next.decode && !next.resetFirst && next.dropFront == 0;
        double floor = m.floor();
        assert !m.packet(1, 0, -100, false, 100).decode; near(m.floor(), floor);
        var gap = m.packet(1, 2, 10.02, false, 10.03);
        assert gap.resetFirst && gap.dropFront == 480 && gap.id == null;
        m.mute(true); assert !m.packet(1, 3, 10.03, false, 10.04).decode;
        m.mute(false); assert m.packet(1, 4, 10.04, false, 10.05).dropFront == 480;
        m.reset(AudioPlayout.Reset.PLAYBACK); near(m.floor(), floor);
        assert !m.packet(1, 4, 10.04, false, 10.05).decode;
        assert m.packet(1, 5, 21, false, 21.01).dropFront == 480;
        m.played(0, 21.01, 21.01);
        assert m.packet(1, 6, 21.01, false, 21.02).id != null;
        m.reset(AudioPlayout.Reset.SESSION); assert m.floor() == null && m.epoch == null;
        m = model(480, 240);
        int discarded = 0;
        for (int k = 0; k < 105; k++) discarded += m.packet(1, k, k * .01, k == 0, k * .01 + .01).output.discards.size();
        assert discarded == 5;
        assert m.reset(AudioPlayout.Reset.PLAYBACK).discards.size() == 100;
        float[] x = new float[480]; Arrays.fill(x, 1);
        AudioPlayout.fade(x, true, 240); assert x[0] < .001 && x[239] > .99 && x[240] == 1;
        float[] y = new float[480]; Arrays.fill(y, 1);
        AudioPlayout.fade(y, false, 240); assert y[479] < .001 && y[240] > .99 && y[239] == 1;
        float[] shortFade = new float[100]; Arrays.fill(shortFade, 1);
        AudioPlayout.fade(shortFade, true, 240);
        assert shortFade[0] < .01 && shortFade[99] > .99;
        for (int k = 1; k < 240; k++) assert x[k] >= x[k - 1];
        float[] sine = new float[480];
        for (int k = 0; k < sine.length; k++) sine[k] = (float) Math.sin(k * .1);
        for (int delta : new int[] {-1, 0, 1}) {
            float[][] changed = AudioPlayout.change(sine, sine, delta);
            assert changed[0].length == 480 + delta && Arrays.equals(changed[0], changed[1]);
            if (delta == 0) assert Arrays.equals(changed[0], sine);
            else {
                int firstDiff = 0;
                while (changed[0][firstDiff] == sine[firstDiff]) firstDiff++;
                assert firstDiff >= 120 && firstDiff <= 360;
                int quietest = 120;
                float level = Float.POSITIVE_INFINITY;
                for (int k = 120; k < 360; k++) {
                    float v = 2 * Math.abs(sine[k]) + 2 * Math.abs(sine[k + 1]);
                    if (v < level) { level = v; quietest = k; }
                }
                assert firstDiff == quietest + (delta > 0 ? 1 : 0);
                float natural = 0, changedJump = 0;
                for (int k = 1; k < sine.length; k++) natural = Math.max(natural, Math.abs(sine[k] - sine[k - 1]));
                for (int k = 1; k < changed[0].length; k++) changedJump = Math.max(changedJump, Math.abs(changed[0][k] - changed[0][k - 1]));
                assert changedJump <= natural * (delta > 0 ? 1.01 : 1.6);
            }
        }
        float[] asymmetricLeft = {8, 6, 4, 0, 1, 3, 5, 7};
        float[] asymmetricRight = {80, 60, 40, 0, 2, 30, 50, 70};
        float[][] inserted = AudioPlayout.change(asymmetricLeft, asymmetricRight, 1);
        assert Arrays.equals(inserted[0], new float[] {8, 6, 4, 0, .5f, 1, 3, 5, 7});
        assert Arrays.equals(inserted[1], new float[] {80, 60, 40, 0, 1, 2, 30, 50, 70});
        float[][] removed = AudioPlayout.change(asymmetricLeft, asymmetricRight, -1);
        assert Arrays.equals(removed[0], new float[] {8, 6, 4, .5f, 3, 5, 7});
        assert Arrays.equals(removed[1], new float[] {80, 60, 40, 1, 30, 50, 70});
        System.out.println("AudioPlayout original numeric/deadline, wrap/gap/mute/reset/waiting and PCM scenarios passed");
    }
}
