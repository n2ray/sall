package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.List;

public final class PhysicalMouseTest {
    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        PhysicalMouse mouse = new PhysicalMouse((action, x, y) -> events.add(action + ":" + x + "," + y));
        mouse.update(0, .25f, .5f, true);
        mouse.update(1, .25f, .5f, true);
        mouse.update(1, .5f, .5f, true);
        mouse.update(0, 1.4f, -.2f, false);
        assert events.equals(List.of("move:0.25,0.5", "move:0.25,0.5", "leftDown:0.25,0.5",
                "move:0.5,0.5", "move:1.0,0.0", "leftUp:1.0,0.0")) : events;
        events.clear();
        mouse.update(2, .5f, .5f, true);
        mouse.update(2, .5f, .5f, true);
        mouse.cancel();
        assert events.stream().filter(s -> s.startsWith("rightDown")).count() == 1;
        assert events.stream().filter(s -> s.startsWith("rightUp")).count() == 1;
        events.clear();
        mouse.update(4, .5f, .5f, true); // The host protocol has no middle button.
        assert events.equals(List.of("move:0.5,0.5")) : events;
        events.clear();
        mouse.update(1, .5f, .5f, false);
        assert events.isEmpty();
        mouse.update(2, .25f, .75f, true);
        mouse.setBlocked(true); // A touchpad gesture owns the pointer now.
        int count = events.size();
        mouse.update(0, .8f, .2f, true);
        assert events.size() == count;
        assert events.get(count - 1).equals("rightUp:0.25,0.75") : events;
        mouse.setBlocked(false);
        mouse.update(0, .8f, .2f, true);
        assert events.get(events.size() - 1).equals("move:0.8,0.2") : events;
        events.clear();
        mouse.update(1, .5f, .5f, true);
        mouse.update(3, .5f, .5f, true);
        mouse.update(2, .5f, .5f, true);
        mouse.update(0, .5f, .5f, true);
        assert events.stream().filter(s -> s.startsWith("leftDown")).count() == 1 : events;
        assert events.stream().filter(s -> s.startsWith("leftUp")).count() == 1 : events;
        assert events.stream().noneMatch(s -> s.startsWith("right")) : events;
        events.clear();
        mouse.update(3, .5f, .5f, true);
        mouse.update(0, .5f, .5f, true);
        assert events.stream().filter(s -> s.startsWith("leftDown")).count() == 1 : events;
        assert events.stream().noneMatch(s -> s.startsWith("right")) : events;
        System.out.println("Physical mouse button checks passed");
    }
}
