package io.github.n2ray.sill;

import java.io.Closeable;
import java.io.IOException;
import java.security.MessageDigest;

/** Independent QR pairing while a stream remains owned by its existing session. */
final class OverlayPairingAuthorization {
    enum Expiry { COMMITTED, EXPIRED, EARLY }
    static final class Attempt {
        private final Object session;
        private final byte[] target;
        private final long deadline;
        private boolean active = true;
        private boolean committed;
        private Closeable transport;
        private Attempt(Object session, byte[] target, long deadline) {
            this.session = session; this.target = target.clone(); this.deadline = deadline;
        }
    }
    private Object session;
    private byte[] streamPin;
    private Attempt current;

    /** A new identity object represents each stream generation, even to the same Mac. */
    void session(Object generation, byte[] pin) {
        if (generation == null || pin == null || pin.length != 32) throw new IllegalArgumentException();
        Closeable close;
        synchronized (this) {
            if (session == generation) throw new IllegalArgumentException("session generation reused");
            close = stop(); session = generation; streamPin = pin.clone();
        }
        close(close);
    }
    void endSession(Object generation) {
        close(detachSession(generation));
    }
    /** Revoke under the store lock, then close the returned transport after releasing it. */
    synchronized Closeable detachSession(Object generation) {
        if (session != generation || generation == null) return null;
        Closeable value = stop(); session = null; streamPin = null;
        return value;
    }
    Attempt begin(Object generation, byte[] targetPin, long deadline, long now) {
        if (targetPin == null || targetPin.length != 32 || deadline <= now) throw new IllegalArgumentException();
        Closeable old;
        Attempt next;
        synchronized (this) {
            if (generation == null || session != generation) return null;
            old = stop(); current = next = new Attempt(generation, targetPin, deadline);
        }
        close(old); return next;
    }
    boolean track(Attempt attempt, Closeable resource, long now) {
        if (resource == null) throw new IllegalArgumentException();
        Closeable old = null; boolean accepted; boolean alreadyTracked = false;
        synchronized (this) {
            accepted = valid(attempt, now);
            if (accepted && attempt.transport == null) attempt.transport = resource;
            else {
                alreadyTracked = accepted && attempt.transport == resource;
                accepted = false;
                if (attempt == current && !valid(attempt, now)) old = stop();
            }
        }
        close(old); if (!accepted && !alreadyTracked && resource != old) close(resource); return accepted;
    }
    /** Call in the network worker's finally, before acquiring the store lock to commit. */
    void finishTransport(Attempt attempt, Closeable resource) {
        synchronized (this) {
            if (attempt != null && attempt.transport == resource) attempt.transport = null;
        }
        close(resource);
    }
    void cancel(Attempt attempt) {
        Closeable close;
        synchronized (this) { if (current != attempt || attempt == null) return; close = stop(); }
        close(close);
    }
    Expiry expire(Attempt attempt, long now) {
        Closeable close;
        synchronized (this) {
            if (attempt == null) throw new IllegalArgumentException();
            if (attempt.committed) return Expiry.COMMITTED;
            if (now < attempt.deadline) return Expiry.EARLY;
            close = current == attempt ? stop() : null;
        }
        close(close);
        return Expiry.EXPIRED;
    }
    /** Call under the store lock; close the returned resource after releasing that lock. */
    synchronized Closeable forget(byte[] pin) {
        if (pin == null || pin.length != 32) throw new IllegalArgumentException();
        if (current == null || !MessageDigest.isEqual(current.target, pin)) return null;
        return stop();
    }
    /** Authenticate proof first; store -> authorization is the only lock order. */
    synchronized boolean commit(Attempt attempt, byte[] authenticatedPin, long now, Runnable save) {
        if (authenticatedPin == null || authenticatedPin.length != 32 || save == null || !valid(attempt, now)
                || attempt.transport != null || !MessageDigest.isEqual(attempt.target, authenticatedPin)) return false;
        attempt.active = false; current = null; attempt.transport = null;
        save.run();
        attempt.committed = true;
        return true;
    }
    synchronized boolean targetsStream(Attempt attempt) {
        return attempt != null && current == attempt && attempt.active && session == attempt.session
                && streamPin != null && MessageDigest.isEqual(streamPin, attempt.target);
    }
    private boolean valid(Attempt attempt, long now) {
        return attempt != null && attempt == current && attempt.active && attempt.session == session && now < attempt.deadline;
    }
    private Closeable stop() {
        if (current == null) return null;
        current.active = false; Closeable value = current.transport; current.transport = null; current = null; return value;
    }
    private static void close(Closeable value) { if (value != null) try { value.close(); } catch (IOException ignored) { } }
}
