package io.github.n2ray.sall;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** Deletions apply to this pairing run, including a peer whose identity is not known yet. */
final class PairingAuthorization {
    private final Set<String> forgotten = new HashSet<>();
    private String peer;
    private Closeable pending;
    PairingAuthorization(byte[] expected) { peer = expected == null ? null : key(expected); }
    synchronized void track(Closeable resource) { check(); pending = resource; }
    synchronized void identify(byte[] pin) { peer = key(pin); check(); }
    synchronized void check() { if (peer != null && forgotten.contains(peer)) throw new CancellationException("Mac was removed during pairing"); }
    synchronized Closeable forget(byte[] pin) {
        String identity = key(pin); forgotten.add(identity);
        return identity.equals(peer) ? pending : null;
    }
    private static String key(byte[] pin) { return java.util.Base64.getEncoder().encodeToString(pin); }
}
