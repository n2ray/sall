package io.github.n2ray.sall;

import java.io.Closeable;
import java.security.MessageDigest;
import java.util.concurrent.CancellationException;

/** A run's pinned identity can be revoked even between handshake completion and session publication. */
final class SessionAuthorization {
    private final byte[] pin;
    private boolean revoked;
    private Closeable resource;
    SessionAuthorization(byte[] pin) { this.pin = pin.clone(); }
    synchronized boolean matches(byte[] expected) { return MessageDigest.isEqual(pin, expected); }
    synchronized void check() { if (revoked) throw new CancellationException("Mac was removed"); }
    synchronized void track(Closeable next) { check(); resource = next; }
    // Close outside the authorization/store locks: pending socket callbacks may also access the store.
    synchronized Closeable revoke() { revoked = true; Closeable current = resource; resource = null; return current; }
}
