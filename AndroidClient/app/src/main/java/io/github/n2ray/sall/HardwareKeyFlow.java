package io.github.n2ray.sall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps each physical press on its original Mac or input-method path until its up. */
final class HardwareKeyFlow {
    enum Action { REMOTE_DOWN, REMOTE_UP, REMOTE_REPEAT, INPUT_METHOD, IGNORE }
    private record Press(int usage, boolean inputMethod, boolean macRepeats) { }
    private final Map<Long, Press> pressed = new LinkedHashMap<>();

    Action down(long identity, int usage, boolean inputMethod, boolean macRepeats, int repeatCount) {
        Press previous = pressed.get(identity);
        if (previous != null) {
            if (previous.inputMethod()) return Action.INPUT_METHOD;
            return repeatCount > 0 && !previous.macRepeats() && repeatable(previous.usage())
                    ? Action.REMOTE_REPEAT : Action.IGNORE;
        }
        if (repeatCount > 0) return Action.IGNORE;
        boolean alreadyDown = !inputMethod && heldRemotely(usage);
        pressed.put(identity, new Press(usage, inputMethod, macRepeats));
        return inputMethod ? Action.INPUT_METHOD : alreadyDown ? Action.IGNORE : Action.REMOTE_DOWN;
    }
    Action up(long identity) {
        Press previous = pressed.remove(identity);
        return previous == null ? Action.IGNORE : previous.inputMethod() ? Action.INPUT_METHOD
                : heldRemotely(previous.usage()) ? Action.IGNORE : Action.REMOTE_UP;
    }
    List<Integer> release() {
        java.util.Set<Integer> ups = new java.util.LinkedHashSet<>();
        for (Press press : pressed.values()) if (!press.inputMethod()) ups.add(press.usage());
        pressed.clear(); return new ArrayList<>(ups);
    }
    private boolean heldRemotely(int usage) {
        for (Press press : pressed.values()) if (!press.inputMethod() && press.usage() == usage) return true;
        return false;
    }
    static boolean repeatable(int usage) { return usage != 0x39 && (usage < 0xe0 || usage > 0xe7); }
}
