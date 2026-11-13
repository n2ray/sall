package io.github.n2ray.sall;

import java.util.List;
import java.util.Set;

public final class TourPolicyTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static TourPolicy.Moment moment(double now, Double picture, double layout, boolean decided,
                                            Double activity, int touches, boolean busy, boolean offered,
                                            boolean accessibility, boolean enabled) {
        return new TourPolicy.Moment(now, picture, layout, decided, activity, touches, busy, offered, accessibility, enabled);
    }
    public static void main(String[] args) {
        var fresh = TourPolicy.Memory.fresh();
        var landscape = TourPolicy.Layout.LANDSCAPE;
        var phone = TourPolicy.Layout.PHONE;
        var touch = TourPolicy.Topic.TOUCH;
        var bar = TourPolicy.Topic.BAR;
        var sharing = TourPolicy.Topic.SHARING;
        var settings = TourPolicy.Topic.SETTINGS;
        var laptop = TourPolicy.Topic.LAPTOP;
        var side = List.of(touch, bar, sharing, settings);
        var upright = List.of(touch, bar, sharing, settings, laptop);
        check(TourPolicy.steps(landscape, false).equals(side), "landscape order");
        check(TourPolicy.steps(phone, true).equals(List.of(bar, sharing, settings, laptop)), "TalkBack leaves touch out");
        check(TourPolicy.targets(bar, phone, false).equals(List.of(TourPolicy.Target.APPS, TourPolicy.Target.MENUS,
                TourPolicy.Target.TEXT_SIZE, TourPolicy.Target.KEYBOARD)), "larger picture targets apps");
        var sideDone = new TourPolicy.Memory(Set.copyOf(side), false);
        check(TourPolicy.owed(phone, false, sideDone).equals(List.of(laptop)), "upright step still owed");
        check(TourPolicy.owed(phone, false, new TourPolicy.Memory(Set.of(), true)).isEmpty(), "skipped stays off");
        check(TourPolicy.Memory.fromNames(List.of("bar", "unknown", "TOUCH", "sharing"), false).names().equals(List.of("bar", "sharing")), "stable saved names");
        check(TourPolicy.upgraded(sideDone, null).equals(new TourPolicy.Upgrade(fresh, true)), "unversioned resets");
        check(TourPolicy.upgraded(sideDone, 1).equals(new TourPolicy.Upgrade(fresh, true)), "old version resets");
        check(TourPolicy.upgraded(sideDone, 3).equals(new TourPolicy.Upgrade(sideDone, false)), "future version preserved");

        check(TourPolicy.automatic(moment(1, null, 0, false, null, 0, false, false, false, true), phone, fresh).equals(TourPolicy.Decision.waitUntil(null)), "wait for picture");
        check(TourPolicy.automatic(moment(10.5, 10.0, 9, false, null, 0, false, false, false, true), phone, fresh).equals(TourPolicy.Decision.waitUntil(11.0)), "wait one beat");
        check(TourPolicy.automatic(moment(12.1, 10.0, 12, false, null, 0, false, false, false, true), phone, fresh).equals(TourPolicy.Decision.waitUntil(13.0)), "rotation restarts beat");
        check(TourPolicy.automatic(moment(12, 10.0, 9, false, 10.0, 0, false, false, false, true), phone, fresh).reason() == TourPolicy.Reason.USED, "activity at picture cancels offer");
        check(TourPolicy.automatic(moment(12, 10.0, 9, false, null, 1, false, false, false, true), phone, fresh).reason() == TourPolicy.Reason.BUSY, "finger held cancels offer");
        check(TourPolicy.automatic(moment(12, 10.0, 9, false, null, 0, false, false, false, true), phone, fresh).steps().equals(upright), "show after quiet beat");
        check(TourPolicy.automatic(moment(12, 10.0, 9, true, null, 0, false, false, false, true), phone, fresh).steps().equals(List.of(laptop)), "only new upright topic after turn");
        check(TourPolicy.automatic(moment(12, 10.0, 9, false, null, 0, false, true, false, true), phone, fresh).reason() == TourPolicy.Reason.NOTHING_OWED, "already offered");

        var run = TourPolicy.run(side);
        check(run != null && run.at() == touch, "automatic run begins at first");
        check(TourPolicy.skip(run, fresh).skipped(), "skip automatic persists");
        check(!TourPolicy.skip(TourPolicy.replay(phone, false, null), fresh).skipped(), "skip replay does not persist");
        var next = TourPolicy.next(run);
        check(next != null && next.at() == bar && next.passed().contains(touch), "next remembers passed");
        var carried = TourPolicy.carry(next, phone, false, TourPolicy.passed(touch, fresh));
        check(carried != null && carried.at() == bar && carried.steps().equals(upright), "rotation retains current and count");
        var last = new TourPolicy.Run(upright, laptop, Set.copyOf(side), false);
        check(TourPolicy.carry(last, landscape, false, sideDone) == null, "rotation ends missing last step");
        var accessibility = TourPolicy.carry(run, landscape, true, fresh);
        check(accessibility != null && accessibility.at() == bar, "accessibility skips touch on rotation");
        var session = new TourPolicy.Session(true, Set.of(landscape), false);
        check(TourPolicy.nextSession(session, true).equals(session), "reconnect retains decision");
        check(TourPolicy.nextSession(new TourPolicy.Session(true, Set.of(phone), true), true).equals(TourPolicy.Session.fresh()), "interrupted run starts fresh");
        check(TourPolicy.nextSession(session, false).equals(TourPolicy.Session.fresh()), "explicit new connection resets session");
        System.out.println("Tour policy checks passed");
    }
}
