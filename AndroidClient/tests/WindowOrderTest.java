package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.List;

public final class WindowOrderTest {
    public static void main(String[] args) {
        List<Long> live = List.of(1L, 2L, 3L);
        List<Long> ordered = WindowOrder.ordered(live, List.of(3L, 9L, 1L));
        assert ordered.equals(List.of(3L, 1L, 2L));
        assert WindowOrder.move(ordered, 1, 2) && ordered.equals(List.of(3L, 2L, 1L));
        assert !WindowOrder.move(ordered, 1, -1) && !WindowOrder.move(ordered, 7, 0);
        assert WindowOrder.capped(List.of(9L, 1L, 3L), live).equals(List.of(1L, 3L, 9L));
        List<Long> many = new ArrayList<>(); for (long i = 0; i < 70; i++) many.add(i);
        assert WindowOrder.capped(many, many).size() == 64;
        System.out.println("Window order checks passed");
    }
}
