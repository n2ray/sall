package io.github.n2ray.sall;

/** Main-thread metadata only; an unknown timestamp never grants a paste suggestion. */
final class ClipboardChangeTracker {
    record Observation(long id, boolean text, boolean fromMac) { }
    private long timestamp, id, next, uncertainThrough;
    private boolean macPending;
    void macWriteStarting() { macPending = true; }
    void macWriteUnidentified(long wallTime) {
        macPending = false; uncertainThrough = Math.max(uncertainThrough, Math.max(1, wallTime));
        timestamp = 0; id = 0;
    }
    Observation macWriteCompleted(long time, boolean isText, long wallTime) {
        if (time <= 0 || !isText || time > wallTime) { macWriteUnidentified(wallTime); return new Observation(0, false, false); }
        uncertainThrough = 0;
        return record(time, true);
    }
    Observation observe(long time, boolean isText, long wallTime) {
        if (macPending) return new Observation(0, false, false);
        if (uncertainThrough != 0) {
            if (!isText || time <= uncertainThrough || time > wallTime || wallTime < uncertainThrough)
                return new Observation(0, false, false);
            uncertainThrough = 0;
        }
        return record(time, isText);
    }
    private Observation record(long time, boolean isText) {
        boolean usable = time > 0 && isText;
        if (!usable) { timestamp = 0; id = 0; return new Observation(0, false, false); }
        if (timestamp != time || id == 0) { timestamp = time; id = ++next; }
        boolean fromMac = macPending; macPending = false;
        return new Observation(id, true, fromMac);
    }
    long timestamp() { return timestamp; }
}
