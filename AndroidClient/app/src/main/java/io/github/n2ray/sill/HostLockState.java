package io.github.n2ray.sill;

/** Session-local interpretation of optional WindowList lock fields. */
final class HostLockState {
    enum Reason { NONE, SCREEN_LOCKED, OTHER_USER }
    enum Shade { NONE, OTHER_USER, UNLOCKING }
    static final long UNLOCK_TIMEOUT_MS = 4000;

    private final boolean advertisedView;
    private Reason reason = Reason.NONE;
    private Shade shade = Shade.NONE;
    private boolean viewOnly, covered, freshFrameRequired;
    private long unlockingAt, generation;
    private long listVersion, presentedListVersion = -1;

    HostLockState(boolean advertisedView) { this.advertisedView = advertisedView; }
    record ListUpdate(boolean blank, long version) { }

    synchronized void requireFreshFrame() { freshFrameRequired = true; generation++; }

    /** Returns true when the displayed frame must be cleared immediately. */
    synchronized boolean onWindowList(boolean locked, boolean offConsole, boolean offConsoleView, long now) {
        listVersion++;
        Reason next = !locked ? Reason.NONE : offConsole ? Reason.OTHER_USER : Reason.SCREEN_LOCKED;
        boolean nextView = advertisedView && next == Reason.OTHER_USER && offConsoleView;
        boolean nextCovered = next == Reason.OTHER_USER && !nextView;
        boolean blank = nextCovered && !covered;
        if (nextCovered != covered) generation++;
        if (blank) freshFrameRequired = true;
        if (nextCovered) {
            shade = Shade.OTHER_USER;
        } else if (covered) {
            // A view-only stream is a new picture too: never expose the old surface during the switch.
            shade = Shade.UNLOCKING;
            unlockingAt = now;
        }
        reason = next; viewOnly = nextView; covered = nextCovered;
        return blank;
    }
    synchronized ListUpdate onWindowListUpdate(boolean locked, boolean offConsole, boolean offConsoleView, long now) {
        return new ListUpdate(onWindowList(locked, offConsole, offConsoleView, now), listVersion);
    }

    /** A timeout dismisses the text, but callers must keep the old frame blank until a fresh keyframe. */
    synchronized void onTime(long now) {
        if (shade == Shade.UNLOCKING && now >= unlockingAt && now - unlockingAt >= UNLOCK_TIMEOUT_MS)
            shade = Shade.NONE;
    }

    /** While covered, discard frames; while recovering, only a fresh keyframe may begin decoding. */
    synchronized boolean shouldDecode(boolean keyframe) { return !covered && (!freshFrameRequired || keyframe); }
    synchronized boolean shouldDecode(boolean keyframe, long seededGeneration) {
        return !covered && (!freshFrameRequired || keyframe || seededGeneration == generation);
    }

    /** Call only after a new keyframe has actually been decoded and presented. */
    synchronized void onKeyframePresented(long token) {
        if (covered || token != generation) return;
        freshFrameRequired = false;
        if (shade == Shade.UNLOCKING) shade = Shade.NONE;
    }

    synchronized Reason reason() { return reason; }
    synchronized Shade shade() { return shade; }
    synchronized boolean viewOnly() { return viewOnly; }
    synchronized boolean isCovered() { return covered; }
    synchronized boolean needsFreshFrame() { return freshFrameRequired; }
    synchronized boolean maySendControl() { return reason != Reason.OTHER_USER; }
    synchronized long generation() { return generation; }
    synchronized long listVersion() { return listVersion; }
    synchronized boolean presentList(long version, Runnable apply) {
        if (version != listVersion) return false;
        presentedListVersion = version; apply.run(); return true;
    }
    synchronized boolean withPresentedList(Runnable apply) {
        if (presentedListVersion != listVersion) return false;
        apply.run(); return true;
    }
}
