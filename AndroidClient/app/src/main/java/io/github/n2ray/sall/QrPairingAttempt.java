package io.github.n2ray.sall;

import java.io.Closeable;
import java.io.IOException;

/** One QR pairing generation owns at most one cancellable transport. */
final class QrPairingAttempt {
    private boolean active = true;
    private Closeable transport;

    synchronized boolean active() { return active; }
    boolean track(Closeable value) {
        synchronized (this) {
            if (active) { transport = value; return true; }
        }
        close(value); return false;
    }
    synchronized void untrack(Closeable value) { if (transport == value) transport = null; }
    void cancel() {
        Closeable value;
        synchronized (this) { active = false; value = transport; transport = null; }
        close(value);
    }
    synchronized boolean complete(Runnable publish) {
        if (!active) return false;
        active = false; transport = null; publish.run(); return true;
    }
    private static void close(Closeable value) {
        if (value != null) try { value.close(); } catch (IOException ignored) { }
    }
}
