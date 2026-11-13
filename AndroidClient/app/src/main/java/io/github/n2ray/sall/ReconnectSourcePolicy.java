package io.github.n2ray.sall;

/** Remembers only a host-confirmed source within one connection run. */
final class ReconnectSourcePolicy {
    private InitialSourcePolicy.Source last = InitialSourcePolicy.Source.UNKNOWN;
    private long window = -1;
    private String launch = "";
    private boolean pending;

    void disconnected() { if (!pending && last != InitialSourcePolicy.Source.UNKNOWN) pending = true; }
    void chosen() { pending = false; last = InitialSourcePolicy.Source.UNKNOWN; }
    boolean pending() { return pending; }

    // Returns null for no request, -1 for desktop, or a window ID.
    Long onList(InitialSourcePolicy.Source source, long id, String launchId, boolean windowPresent, boolean allowed) {
        if (!allowed) return null;
        if (pending && source == InitialSourcePolicy.Source.NONE) {
            pending = false;
            if (last == InitialSourcePolicy.Source.WINDOW && windowPresent
                    && (launch.isEmpty() || launchId.isEmpty() || launch.equals(launchId))) return window;
            return -1L;
        }
        if (source == InitialSourcePolicy.Source.DESKTOP || (source == InitialSourcePolicy.Source.WINDOW && id >= 0)) {
            pending = false;
            last = source;
            window = id;
            launch = launchId;
        }
        return null;
    }
    long window() { return window; }
}
