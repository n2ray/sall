package io.github.n2ray.sill;

/** Main-thread policy. Change IDs are caller-owned metadata, never clipboard contents. */
final class ClipboardChipState {
    static final long LIFETIME_MS = 20_000, NOTE_MS = 3_000;
    enum Note { SENT, PASTE_REQUESTED, FAILED, TOO_LONG }
    enum Receipt { STALE, TIMELY, LATE }

    private long session, lastMs, shownMs, pendingToken, noteUntil;
    private Long current, offered, handled, pendingChange, receiptChange, noteChange;
    private boolean foreground, covered, accessibility, eligible, hasText, pending, late, receiptKnown;
    private boolean restored, observed;
    private Note note;

    void newSession(long id, long now) {
        advance(now); session = id; current = offered = pendingChange = receiptChange = null;
        eligible = hasText = pending = late = receiptKnown = false; note = null; noteChange = null; shownMs = 0;
    }
    // Restore only once at process startup, before observing any clipboard changes.
    boolean restoreHandled(Long changeId) {
        if (restored || observed) return false;
        handled = changeId; restored = true; return true;
    }
    void context(boolean foreground, boolean covered, boolean accessibility, long now) {
        advance(now); this.foreground = foreground; this.covered = covered; this.accessibility = accessibility;
    }
    void observe(long id, long changeId, boolean hasText, boolean eligible, long now) {
        if (session != id) return; advance(now); observed = true;
        if (current == null || current.longValue() != changeId) {
            offered = null; shownMs = 0;
            if (noteChange != null && noteChange.longValue() != changeId) { note = null; noteChange = null; }
        }
        current = changeId; this.hasText = hasText; this.eligible = eligible;
        if (!hasText || !eligible) { offered = null; shownMs = 0; }
        else offer();
    }
    void dismiss(long id, long now) {
        if (session != id || offered == null) return; advance(now);
        if (offered == null) return;
        handled = offered; offered = null; shownMs = 0;
    }
    // The caller observes a Mac-written clipboard ID first; old IDs cannot suppress newer content.
    boolean markHandled(long id, long changeId, long now) {
        if (session != id || current == null || current.longValue() != changeId) return false;
        advance(now); handled = changeId;
        if (current.equals(offered)) { offered = null; shownMs = 0; }
        return true;
    }
    boolean beginReceipt(long id, long token, long changeId, long now) {
        if (session != id || !eligible || !hasText || current == null || current.longValue() != changeId) return false;
        advance(now);
        pending = true; late = false; pendingToken = token; pendingChange = changeId;
        receiptKnown = true; receiptChange = changeId;
        note = null; noteChange = null;
        offered = null; shownMs = 0; return true;
    }
    boolean receiptLate(long id, long token, long now) {
        if (session != id || !pending || pendingToken != token || late) return false; advance(now);
        late = true; offer(); return true;
    }
    Receipt receipt(long id, long token, long now) {
        if (session != id || !pending || pendingToken != token) return Receipt.STALE; advance(now);
        Receipt result = late ? Receipt.LATE : Receipt.TIMELY;
        if (pendingChange != null && pendingChange.equals(current)) {
            handled = pendingChange;
            if (pendingChange.equals(offered)) { offered = null; shownMs = 0; }
        }
        pending = false; pendingChange = null; offer(); return result;
    }
    boolean receiptFailed(long id, long token, long now) {
        if (session != id || !pending || pendingToken != token) return false;
        advance(now); pending = false; pendingChange = null; offer(); return true;
    }
    boolean noteForReceipt(long id, long token, Note value, long now) {
        if (session != id || !receiptKnown || pendingToken != token || value == null
                || current == null || !current.equals(receiptChange)) return false;
        note(id, value, now); noteChange = receiptChange; return true;
    }
    void note(long id, Note value, long now) {
        if (session != id || value == null) return; advance(now);
        note = value; noteChange = null;
        noteUntil = lastMs > Long.MAX_VALUE - NOTE_MS ? Long.MAX_VALUE : lastMs + NOTE_MS;
    }
    void advance(long now) {
        now = Math.max(now, lastMs);
        if (note != null && now >= noteUntil) { lastMs = Math.max(lastMs, noteUntil); note = null; noteChange = null; }
        if (counts()) shownMs = Math.min(LIFETIME_MS, shownMs + Math.min(LIFETIME_MS, now - lastMs));
        lastMs = now;
        if (offered != null && shownMs >= LIFETIME_MS) { handled = offered; offered = null; shownMs = 0; }
    }
    boolean visible() { return foreground && !covered && note == null && offered != null; }
    Note visibleNote() { return foreground && !covered ? note : null; }
    Long offered() { return offered; }
    Long handled() { return handled; }
    long nextWake(long now) {
        advance(now); if (note != null) return noteUntil;
        if (!counts()) return Long.MAX_VALUE;
        long remaining = LIFETIME_MS - shownMs;
        return lastMs > Long.MAX_VALUE - remaining ? Long.MAX_VALUE : lastMs + remaining;
    }
    private boolean counts() { return visible() && !accessibility; }
    private void offer() {
        if (eligible && hasText && current != null && !current.equals(handled)
                && (!pending || late) && offered == null) { offered = current; shownMs = 0; }
    }
}
