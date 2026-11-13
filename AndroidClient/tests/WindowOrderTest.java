package io.github.n2ray.sall;

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
        assert WindowOrder.dragTarget(1, 49, 100, 3) == 1;
        assert WindowOrder.dragTarget(1, 50, 100, 3) == 2;
        assert WindowOrder.dragTarget(1, -49, 100, 3) == 1;
        assert WindowOrder.dragTarget(1, -50, 100, 3) == 0;
        assert WindowOrder.dragTarget(1, -500, 100, 3) == 0;
        assert WindowOrder.dragTarget(1, 0, 0, 3) == -1;
        assert WindowOrder.dragSlotOffset(0, 0, 2) == 0;
        assert WindowOrder.dragSlotOffset(1, 0, 2) == -1;
        assert WindowOrder.dragSlotOffset(2, 0, 2) == -1;
        assert WindowOrder.dragSlotOffset(3, 0, 2) == 0;
        assert WindowOrder.dragSlotOffset(0, 2, 0) == 1;
        assert WindowOrder.dragSlotOffset(1, 2, 0) == 1;
        assert WindowOrder.dragSlotOffset(2, 2, 0) == 0;
        assert WindowOrder.dragSlotOffset(1, 1, 1) == 0;
        List<Long> original = List.of(1L, 2L, 3L);
        List<Long> preview = new ArrayList<>(original);
        assert WindowOrder.move(preview, 1, WindowOrder.dragTarget(0, 51, 100, 3));
        assert preview.equals(List.of(2L, 1L, 3L));
        assert WindowOrder.move(preview, 1, WindowOrder.dragTarget(0, 151, 100, 3));
        assert preview.equals(List.of(2L, 3L, 1L));
        assert original.equals(List.of(1L, 2L, 3L)); // Discarding a preview restores the committed order.
        assert WindowOrder.retainMissing(preview, List.of(1L, 7L, 2L, 3L)).equals(List.of(2L, 3L, 1L, 7L));
        List<Long> many = new ArrayList<>(); for (long i = 0; i < 70; i++) many.add(i);
        assert WindowOrder.capped(many, many).size() == 64;
        System.out.println("Window order checks passed");
    }
}
