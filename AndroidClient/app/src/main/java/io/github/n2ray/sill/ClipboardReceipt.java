package io.github.n2ray.sill;

import java.util.Arrays;

/** A paste is allowed only once, after the matching timely receipt. */
final class ClipboardReceipt {
    private long generation, deadline;
    private byte[] nonce;
    private boolean ready;

    synchronized long begin(byte[] value, long now) {
        generation++; nonce = value.clone(); deadline = now + 4000; ready = false;
        return generation;
    }
    synchronized boolean pending(long token) { return generation == token && nonce != null; }
    synchronized boolean receive(byte[] value, long now, long token) {
        if (!pending(token) || now >= deadline || !Arrays.equals(nonce, value)) return false;
        nonce = null; ready = true; return true;
    }
    synchronized boolean expire(long token, long now) {
        if (!pending(token) || now < deadline) return false;
        cancel(); return true;
    }
    synchronized boolean take(long token) {
        if (generation != token || !ready) return false;
        ready = false; return true;
    }
    synchronized void cancel(long token) { if (generation == token) cancel(); }
    synchronized void cancel() { generation++; nonce = null; ready = false; }
}
