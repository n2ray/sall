package io.github.n2ray.sall;

import java.security.MessageDigest;

/** Holds verified metadata only for the exact live TLS session that supplied it. */
final class SessionMacInfoCache<T> {
    private Object owner;
    private byte[] fingerprint;
    private double issuedAt;
    private T value;
    synchronized void bind(Object session, byte[] pin) {
        if (session == null || pin == null || pin.length != 32) throw new IllegalArgumentException();
        owner = session; fingerprint = pin.clone(); issuedAt = 0; value = null;
    }
    synchronized void clear(Object session) {
        if (session == owner && session != null) { owner = null; fingerprint = null; issuedAt = 0; value = null; }
    }
    synchronized void offer(Object session, byte[] pin, double timestamp, T update) {
        if (!matches(session, pin) || update == null || !Double.isFinite(timestamp) || timestamp <= issuedAt) return;
        issuedAt = timestamp; value = update;
    }
    synchronized T get(Object session, byte[] pin) { return matches(session, pin) ? value : null; }
    private boolean matches(Object session, byte[] pin) {
        return session != null && session == owner && fingerprint != null && pin != null && pin.length == 32
                && MessageDigest.isEqual(fingerprint, pin);
    }
}
