package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** First-run tour decisions; times are monotonic seconds. */
final class TourPolicy {
    static final int VERSION = 2;
    static final double BEAT = 1.0;

    enum Topic { TOUCH, BAR, SHARING, SETTINGS, LAPTOP }
    enum Layout { LANDSCAPE, PORTRAIT, PHONE }
    enum Target { STREAM, APPS, STRIP, MENUS, TEXT_SIZE, KEYBOARD, SETTINGS, KEYS, TRACKPAD }
    enum Kind { SHOW, WAIT, PASS }
    enum Reason { NOTHING_OWED, USED, BUSY }

    record Memory(Set<Topic> seen, boolean skipped) {
        Memory { seen = Set.copyOf(seen); }
        static Memory fresh() { return new Memory(Set.of(), false); }
        static Memory fromNames(List<String> names, boolean skipped) {
            EnumSet<Topic> seen = EnumSet.noneOf(Topic.class);
            for (String name : names) {
                for (Topic topic : Topic.values()) if (topic.name().toLowerCase(java.util.Locale.ROOT).equals(name)) seen.add(topic);
            }
            return new Memory(seen, skipped);
        }
        List<String> names() {
            List<String> names = new ArrayList<>();
            for (Topic topic : Topic.values()) if (seen.contains(topic)) names.add(topic.name().toLowerCase(java.util.Locale.ROOT));
            return List.copyOf(names);
        }
    }
    record Upgrade(Memory memory, boolean write) { }
    record Moment(double now, Double pictureAt, double layoutAt, boolean decided, Double lastActivityAt,
                  int touchesDown, boolean busy, boolean offered, boolean accessibility, boolean enabled) { }
    record Decision(Kind kind, List<Topic> steps, Double until, Reason reason) {
        Decision { steps = List.copyOf(steps); }
        static Decision show(List<Topic> steps) { return new Decision(Kind.SHOW, steps, null, null); }
        static Decision waitUntil(Double until) { return new Decision(Kind.WAIT, List.of(), until, null); }
        static Decision pass(Reason reason) { return new Decision(Kind.PASS, List.of(), null, reason); }
    }
    record Session(boolean decided, Set<Layout> offered, boolean running) {
        Session { offered = Set.copyOf(offered); }
        static Session fresh() { return new Session(false, Set.of(), false); }
    }
    record Run(List<Topic> steps, Topic at, Set<Topic> passed, boolean replay) {
        Run {
            steps = List.copyOf(steps);
            passed = Set.copyOf(passed);
            if (!steps.contains(at)) throw new IllegalArgumentException("Tour step is not in run");
        }
        int index() { return steps.indexOf(at); }
        boolean isLast() { return index() == steps.size() - 1; }
    }

    static List<Topic> steps(Layout layout, boolean accessibility) {
        List<Topic> result = new ArrayList<>();
        if (!accessibility) result.add(Topic.TOUCH);
        result.addAll(List.of(Topic.BAR, Topic.SHARING, Topic.SETTINGS));
        if (layout != Layout.LANDSCAPE) result.add(Topic.LAPTOP);
        return List.copyOf(result);
    }

    static List<Target> targets(Topic topic, Layout layout, boolean thumbnails) {
        return switch (topic) {
            case TOUCH -> List.of(Target.STREAM);
            case BAR -> layout == Layout.PHONE && !thumbnails
                    ? List.of(Target.APPS, Target.MENUS, Target.TEXT_SIZE, Target.KEYBOARD)
                    : layout == Layout.PORTRAIT
                    ? List.of(Target.STRIP, Target.MENUS, Target.TEXT_SIZE)
                    : List.of(Target.STRIP, Target.MENUS, Target.TEXT_SIZE, Target.KEYBOARD);
            case SHARING, SETTINGS -> List.of(Target.SETTINGS);
            case LAPTOP -> List.of(Target.KEYS, Target.TRACKPAD);
        };
    }

    static List<Topic> owed(Layout layout, boolean accessibility, Memory memory) {
        if (memory.skipped()) return List.of();
        return steps(layout, accessibility).stream().filter(topic -> !memory.seen().contains(topic)).toList();
    }

    static Decision automatic(Moment moment, Layout layout, Memory memory) {
        if (!moment.enabled() || moment.offered()) return Decision.pass(Reason.NOTHING_OWED);
        if (moment.pictureAt() == null) return Decision.waitUntil(null);
        List<Topic> list = owed(layout, moment.accessibility(), memory);
        if (moment.decided()) list = list.stream().filter(topic -> layout != Layout.LANDSCAPE && topic == Topic.LAPTOP).toList();
        double opens = Math.max(moment.pictureAt(), moment.layoutAt());
        if (list.isEmpty()) return Decision.pass(Reason.NOTHING_OWED);
        if (moment.lastActivityAt() != null && moment.lastActivityAt() >= opens) return Decision.pass(Reason.USED);
        if (moment.now() < opens + BEAT) return Decision.waitUntil(opens + BEAT);
        if (moment.touchesDown() > 0 || moment.busy()) return Decision.pass(Reason.BUSY);
        return Decision.show(list);
    }

    static Upgrade upgraded(Memory saved, Integer savedVersion) {
        return savedVersion == null || savedVersion < VERSION ? new Upgrade(Memory.fresh(), true) : new Upgrade(saved, false);
    }
    static Memory passed(Topic topic, Memory memory) {
        EnumSet<Topic> seen = EnumSet.noneOf(Topic.class);
        seen.addAll(memory.seen()); seen.add(topic);
        return new Memory(seen, memory.skipped());
    }
    static Memory skip(Run run, Memory memory) {
        return run.replay() ? memory : new Memory(memory.seen(), true);
    }
    static Session nextSession(Session last, boolean reconnected) {
        return reconnected && last.decided() && !last.running() ? new Session(true, last.offered(), false) : Session.fresh();
    }
    static Run run(List<Topic> owed) { return owed.isEmpty() ? null : new Run(owed, owed.get(0), Set.of(), false); }
    static Run replay(Layout layout, boolean accessibility, Topic start) {
        List<Topic> steps = steps(layout, accessibility);
        return steps.isEmpty() ? null : new Run(steps, start != null && steps.contains(start) ? start : steps.get(0), Set.of(), true);
    }
    static Run next(Run run) {
        int next = run.index() + 1;
        if (next == run.steps().size()) return null;
        EnumSet<Topic> passed = EnumSet.noneOf(Topic.class);
        passed.addAll(run.passed()); passed.add(run.at());
        return new Run(run.steps(), run.steps().get(next), passed, run.replay());
    }
    static Run carry(Run run, Layout layout, boolean accessibility, Memory memory) {
        List<Topic> available = steps(layout, accessibility);
        List<Topic> list = run.replay() ? available : available.stream()
                .filter(topic -> !memory.seen().contains(topic) || run.passed().contains(topic)).toList();
        if (list.contains(run.at())) return new Run(list, run.at(), run.passed(), run.replay());
        for (Topic topic : list) if (topic.ordinal() > run.at().ordinal() && !run.passed().contains(topic))
            return new Run(list, topic, run.passed(), run.replay());
        return null;
    }
}
