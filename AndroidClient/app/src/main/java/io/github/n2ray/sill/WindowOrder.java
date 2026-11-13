package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.List;

final class WindowOrder {
    static List<Long> ordered(List<Long> live, List<Long> saved) {
        List<Long> result = new ArrayList<>();
        for (Long id : saved) if (live.contains(id) && !result.contains(id)) result.add(id);
        for (Long id : live) if (!result.contains(id)) result.add(id);
        return result;
    }
    static boolean move(List<Long> values, long id, int target) {
        int from = values.indexOf(id);
        if (from < 0 || target < 0 || target >= values.size() || from == target) return false;
        values.remove(from); values.add(target, id); return true;
    }
    static int dragTarget(int start, float displacement, int slot, int count) {
        if (slot <= 0 || count <= 0) return -1;
        float distance = displacement / slot;
        int shift = (int) Math.copySign(Math.floor(Math.abs(distance) + 0.5), distance);
        return Math.max(0, Math.min(count - 1, start + shift));
    }
    static int dragSlotOffset(int index, int from, int target) {
        return index > from && index <= target ? -1 : index < from && index >= target ? 1 : 0;
    }
    static List<Long> retainMissing(List<Long> visible, List<Long> saved) {
        List<Long> result = new ArrayList<>(visible);
        for (Long id : saved) if (!result.contains(id)) result.add(id);
        return result;
    }
    static List<Long> capped(List<Long> order, List<Long> live) {
        List<Long> result = new ArrayList<>();
        for (Long id : order) if (live.contains(id) && !result.contains(id)) result.add(id);
        for (Long id : order) if (!live.contains(id) && !result.contains(id)) result.add(id);
        return new ArrayList<>(result.subList(0, Math.min(64, result.size())));
    }
}
