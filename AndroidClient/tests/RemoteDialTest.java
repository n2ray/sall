package io.github.n2ray.sill;

import java.io.Closeable;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.SSLHandshakeException;

public final class RemoteDialTest {
    static final class Resource implements Closeable {
        volatile boolean closed;
        public void close() { closed = true; }
    }
    public static void main(String[] args) throws Exception {
        byte[] pin = new byte[32], other = new byte[32]; other[0] = 2;
        var lan = new SavedMacs.Endpoint("192.168.1.2", 7455);
        var vpn = new SavedMacs.Endpoint("100.87.1.2", 7455);
        var name = new SavedMacs.Endpoint("MAC.ts.net", 7455);
        var manual = new SavedMacs.Endpoint("manual.example", 7455);
        var wrong = new SavedMacs.Endpoint("other.example", 7455);
        var one = SavedMacs.Identity.pairingResult(pin, null, null, null);
        var two = SavedMacs.Identity.pairingResult(other, null, null, null);
        var records = List.of(new SavedMacs.Mac(one, List.of(lan, vpn, name, new SavedMacs.Endpoint("mac.ts.net", 7455)), lan, 0),
                new SavedMacs.Mac(two, List.of(wrong), wrong, 0));
        check(RemoteDial.order(records, pin, lan, false).equals(List.of(lan, name, vpn)), "lastWorked, VPN order, dedup and identity isolation");
        check(RemoteDial.order(records, pin, manual, true).equals(List.of(manual, lan, name, vpn)), "explicit route retained first");
        AtomicInteger calls = new AtomicInteger();
        Resource first = new Resource(), winner = new Resource();
        var result = RemoteDial.connect(List.of(lan, vpn), pin, new RemoteDial.Attempt(), (endpoint, identity, attempt) -> {
            check(Arrays.equals(pin, identity), "same full identity on every attempt");
            identity[0] = 99;
            if (calls.getAndIncrement() == 0) { attempt.attach(first); throw new ConnectException("refused"); }
            attempt.attach(winner); return winner;
        });
        check(calls.get() == 2 && first.closed && result.endpoint().equals(vpn) && !winner.closed, "network fallback owns winner only");
        winner.close();
        calls.set(0);
        try {
            RemoteDial.connect(List.of(lan, vpn), pin, new RemoteDial.Attempt(), (endpoint, identity, attempt) -> {
                calls.incrementAndGet(); throw new SSLHandshakeException("pin mismatch");
            });
            throw new AssertionError("TLS failure accepted");
        } catch (SSLHandshakeException expected) { check(calls.get() == 1, "identity failure must stop fallback"); }
        calls.set(0);
        var removed = new GoodbyePolicy.Ended(GoodbyePolicy.outcome(new GoodbyePolicy.Goodbye("removed", null, null, null), "Mac", true));
        try {
            RemoteDial.connect(List.of(lan, vpn), pin, new RemoteDial.Attempt(), (endpoint, identity, attempt) -> {
                calls.incrementAndGet();
                try { throw removed; }
                catch (Exception failure) {
                    RemoteDial.closeAfterFailure(() -> { throw new java.net.SocketException("close failed"); }, failure);
                    throw failure;
                }
            });
            throw new AssertionError("removed accepted");
        } catch (GoodbyePolicy.Ended expected) {
            check(expected == removed && calls.get() == 1 && expected.getSuppressed().length == 1,
                    "socket close failure cannot mask terminal goodbye or enable route fallback");
        }
        for (boolean cancel : new boolean[]{false, true}) {
            var attempt = new RemoteDial.Attempt();
            var socket = new Resource();
            var started = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            long before = System.nanoTime();
            Thread caller = new Thread(() -> {
                try { RemoteDial.connect(List.of(lan), pin, attempt, (endpoint, identity, pending) -> {
                    pending.attach(socket); started.countDown();
                    // Simulate DNS/TLS work that ignores interruption until explicitly released.
                    try { while (true) { try { release.await(); break; } catch (InterruptedException ignored) { } } return socket; }
                    finally { finished.countDown(); }
                }, cancel ? 2000 : 150); } catch (Throwable e) { error.set(e); }
            });
            caller.start(); check(started.await(1, TimeUnit.SECONDS), "worker started");
            if (cancel) attempt.close();
            caller.join(1200);
            check(!caller.isAlive() && socket.closed, "bounded caller and closed pending socket");
            check(cancel ? error.get() instanceof CancellationException : error.get() instanceof SocketTimeoutException, "cancel/timeout surfaced");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 1200, "deadline independent of uninterruptible worker");
            release.countDown(); check(finished.await(1, TimeUnit.SECONDS), "worker released");
        }
        var cancelled = new RemoteDial.Attempt(); cancelled.close(); Resource late = new Resource();
        try { cancelled.attach(late); throw new AssertionError("late socket attached"); }
        catch (CancellationException expected) { check(late.closed, "late socket closed before connecting"); }
        System.out.println("RemoteDialTest passed: identity-safe ordering, fallback, TLS stop, cancellation, deadline, late-socket closure");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
