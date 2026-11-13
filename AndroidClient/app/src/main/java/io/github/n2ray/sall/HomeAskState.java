package io.github.n2ray.sall;

/** Owns cancellation of one request, including a reply arriving after the dialog closed. */
final class HomeAskState {
    private boolean cancelled, shown, completed;
    static long retryMillis(Object value) {
        double seconds = value instanceof Number number ? number.doubleValue() : 1;
        if (!Double.isFinite(seconds)) seconds = 1;
        return Math.round(Math.max(.2, Math.min(10, seconds)) * 1000);
    }
    synchronized boolean shown() {
        if (cancelled) return true;
        shown = true; return false;
    }
    synchronized boolean cancel() {
        if (cancelled) return false;
        cancelled = true;
        boolean withdraw = shown && !completed;
        shown = false; return withdraw;
    }
    synchronized boolean canComplete() { return !cancelled && shown && !completed; }
    synchronized boolean complete() {
        if (!canComplete()) return false;
        completed = true; return true;
    }
    synchronized boolean isShown() { return shown && !cancelled && !completed; }
}
