package io.github.n2ray.sill;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SessionAuthorizationTest {
    static final class Resource implements Closeable {
        volatile boolean closed;
        public void close() { closed = true; }
    }
    public static void main(String[] args) throws Exception {
        byte[] pin = new byte[32], other = new byte[32]; other[0] = 1;
        SessionAuthorization handshaking = new SessionAuthorization(pin);
        Resource pending = new Resource(); handshaking.track(pending);
        check(!handshaking.matches(other), "forget of another identity does not cancel this run");
        Closeable cancelled = handshaking.revoke(); cancelled.close();
        check(pending.closed, "forget during handshake closes pending socket");
        try { handshaking.track(new Resource()); throw new AssertionError("late handshake published"); }
        catch (CancellationException expected) { }

        SessionAuthorization connected = new SessionAuthorization(pin);
        Resource active = new Resource(); connected.track(active);
        connected.revoke().close();
        check(active.closed, "forget after connection closes active session");
        try { connected.track(new Resource()); throw new AssertionError("revoked reconnect started"); }
        catch (CancellationException expected) { }

        // Reproduce handshake completed/Attempt claimed, but session not yet published.
        Object storeLock = new Object();
        SessionAuthorization racing = new SessionAuthorization(pin);
        RemoteDial.Attempt attempt = new RemoteDial.Attempt(); racing.track(attempt); attempt.claim();
        CountDownLatch ready = new CountDownLatch(1), forgotten = new CountDownLatch(1);
        AtomicBoolean published = new AtomicBoolean(), rejected = new AtomicBoolean();
        Resource late = new Resource();
        Thread completion = new Thread(() -> {
            ready.countDown();
            try {
                if (!forgotten.await(1, TimeUnit.SECONDS)) throw new AssertionError("forget did not run");
                try (late) { synchronized (storeLock) { racing.track(late); published.set(true); } }
            } catch (CancellationException expected) { rejected.set(true); }
            catch (InterruptedException e) { throw new AssertionError(e); }
        });
        completion.start(); check(ready.await(1, TimeUnit.SECONDS), "completion waiting");
        Closeable detached;
        synchronized (storeLock) { detached = racing.revoke(); }
        if (detached != null) detached.close(); forgotten.countDown(); completion.join(1500);
        check(!completion.isAlive() && rejected.get() && !published.get() && late.closed,
                "forget serializes with publication and late winner closes without becoming active");
        System.out.println("SessionAuthorizationTest passed: handshake forget, active forget, different pin, claimed-attempt/publication race, revoked retry");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
