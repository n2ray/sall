package io.github.n2ray.sill;

/** iOS starts an ordinary newly connected session on Desktop when its initial source is empty. */
final class InitialSourcePolicy {
    enum Source { NONE, DESKTOP, WINDOW, UNKNOWN }
    private boolean listed, chosen, pending;
    boolean listed() { return listed; }
    void chosen() { chosen = true; pending = false; }
    boolean onList(Source source, boolean allowed) {
        if (!listed) {
            listed = true;
            pending = source == Source.NONE && !chosen;
        } else if (source == Source.WINDOW || source == Source.DESKTOP) {
            pending = false;
        }
        if (!pending || chosen || source != Source.NONE || !allowed) return false;
        pending = false;
        return true;
    }
}
