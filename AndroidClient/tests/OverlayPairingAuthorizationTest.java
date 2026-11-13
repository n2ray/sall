package io.github.n2ray.sall;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicInteger;

public final class OverlayPairingAuthorizationTest {
    static final class Socket implements Closeable { boolean closed; public void close() { closed = true; } }
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        OverlayPairingAuthorization p = new OverlayPairingAuthorization();
        Object stream = new Object(), newStream = new Object();
        byte[] mac = new byte[32], other = new byte[32]; other[0] = 1;
        AtomicInteger saved = new AtomicInteger();
        check(OverlayPairingAuthorization.matchesSession(mac, mac.clone()), "same Mac accepted");
        check(!OverlayPairingAuthorization.matchesSession(mac, other), "different Mac rejected");
        check(!OverlayPairingAuthorization.matchesSession(null, mac), "missing identity rejected");
        check(!OverlayPairingAuthorization.matchesSession(new byte[31], mac), "invalid fingerprint rejected");
        p.session(stream, mac);
        var first = p.begin(stream, other, 100, 0);
        check(!p.targetsStream(first), "different Mac does not acquire stream identity");
        Socket a = new Socket(); check(p.track(first, a, 0), "pair socket tracked");
        p.cancel(first); check(a.closed, "cancel closes pairing socket");
        check(!p.commit(first, other, 1, saved::incrementAndGet), "cancel blocks late result");
        Socket late = new Socket(); check(!p.track(first, late, 1) && late.closed, "late socket closed");
        var second = p.begin(stream, mac, 100, 1);
        check(p.targetsStream(second), "matching Mac recognized without changing stream");
        check(!p.commit(second, other, 2, saved::incrementAndGet), "different authenticated key rejected");
        p.forget(other); check(p.targetsStream(second), "unrelated removal leaves pairing alone");
        Socket b = new Socket(); p.track(second, b, 2); p.forget(mac).close();
        check(b.closed && !p.commit(second, mac, 3, saved::incrementAndGet), "forget rejects queued proof");
        var third = p.begin(stream, other, 10, 3); Socket c = new Socket(); p.track(third, c, 4);
        check(!p.commit(third, other, 10, saved::incrementAndGet), "deadline is exclusive");
        p.expire(third, 10); check(c.closed, "timeout closes socket");
        var fourth = p.begin(stream, other, 100, 10); Socket d = new Socket(); p.track(fourth, d, 11);
        p.session(newStream, mac); check(d.closed, "replaced stream cancels attempt");
        check(!p.commit(fourth, other, 12, saved::incrementAndGet), "old session callback rejected");
        check(p.begin(stream, mac, 100, 12) == null, "old stream cannot begin");
        var fifth = p.begin(newStream, other, 100, 12);
        p.endSession(stream); check(p.commit(fifth, other, 13, saved::incrementAndGet), "old end cannot cancel current session");
        check(saved.get() == 1 && !p.commit(fifth, other, 14, saved::incrementAndGet), "one publication only");
        check(p.expire(fifth, 100) == OverlayPairingAuthorization.Expiry.COMMITTED,
                "late UI timeout must preserve a previously committed result");
        var sixth = p.begin(newStream, mac, 100, 14); p.endSession(newStream);
        check(!p.commit(sixth, mac, 15, saved::incrementAndGet), "session end revokes attempt");
        Object detachedSession = new Object(); p.session(detachedSession, mac);
        var detached = p.begin(detachedSession, other, 100, 15); Socket f = new Socket(); p.track(detached, f, 15);
        Closeable detachedSocket = p.detachSession(detachedSession);
        check(detachedSocket == f && !f.closed, "detach transfers resource for closing outside store lock");
        check(!p.commit(detached, other, 16, saved::incrementAndGet), "detach rejects queued result");
        detachedSocket.close(); check(f.closed, "detached resource closed by caller");
        p.session(new Object(), mac);
        Object owner = new Object(); p.session(owner, mac);
        byte[] mutable = other.clone(); var pinned = p.begin(owner, mutable, 100, 15); mutable[0] = 9;
        Socket e = new Socket(), duplicate = new Socket();
        check(p.track(pinned, e, 16), "first track accepted");
        check(!p.track(pinned, e, 16) && !e.closed, "duplicate track must not close live transport");
        check(!p.track(pinned, duplicate, 16) && duplicate.closed, "second transport rejected and closed");
        check(!p.commit(pinned, other, 17, saved::incrementAndGet), "tracked socket blocks commit");
        p.finishTransport(pinned, e); check(e.closed, "transport closed before persistence");
        check(!p.commit(pinned, mutable, 17, saved::incrementAndGet), "mutating supplied pin cannot change target");
        try { p.commit(pinned, other, 17, () -> { throw new IllegalStateException("save failed"); }); throw new AssertionError("save must throw"); }
        catch (IllegalStateException expected) { check(e.closed, "failed save leaves transport closed"); }
        check(!p.commit(pinned, other, 18, saved::incrementAndGet), "failed save is not reusable");
        check(p.expire(pinned, 100) == OverlayPairingAuthorization.Expiry.EXPIRED,
                "failed save must not claim a committed result");
        OverlayPairingAuthorization timed = new OverlayPairingAuthorization(); Object timedSession = new Object();
        timed.session(timedSession, mac); var timedAttempt = timed.begin(timedSession, other, 10, 0);
        java.util.concurrent.CountDownLatch saving = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> timedFailure = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<OverlayPairingAuthorization.Expiry> result = new java.util.concurrent.atomic.AtomicReference<>();
        Thread saver = new Thread(() -> { try {
            check(timed.commit(timedAttempt, other, 9, () -> { saving.countDown(); await(release); }), "commit accepted");
        } catch (Throwable error) { timedFailure.compareAndSet(null, error); } });
        Thread timer = new Thread(() -> { try { result.set(timed.expire(timedAttempt, 10)); }
            catch (Throwable error) { timedFailure.compareAndSet(null, error); } });
        saver.setDaemon(true); timer.setDaemon(true); saver.start();
        check(saving.await(2, java.util.concurrent.TimeUnit.SECONDS), "save entered before timeout");
        timer.start(); release.countDown(); saver.join(2000); timer.join(2000);
        check(!saver.isAlive() && !timer.isAlive() && timedFailure.get() == null, "commit/timeout race completed");
        check(result.get() == OverlayPairingAuthorization.Expiry.COMMITTED, "timer cannot replace successful save");
        for (int i = 0; i < 500; i++) {
            OverlayPairingAuthorization race = new OverlayPairingAuthorization(); Object generation = new Object(); Object store = new Object();
            race.session(generation, mac); var pending = race.begin(generation, other, 100, 0);
            AtomicInteger persisted = new AtomicInteger(); java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            Thread commit = new Thread(() -> {
                try { await(start); synchronized (store) { race.commit(pending, other, 1, persisted::incrementAndGet); } }
                catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            Thread forget = new Thread(() -> {
                try { await(start); synchronized (store) { race.forget(other); persisted.set(0); } }
                catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            commit.setDaemon(true); forget.setDaemon(true);
            commit.start(); forget.start(); start.countDown(); commit.join(2000); forget.join(2000);
            check(!commit.isAlive() && !forget.isAlive(), "concurrent workers must finish");
            if (failure.get() != null) throw new AssertionError("concurrent worker failed", failure.get());
            check(persisted.get() == 0, "commit and forget are serialized by store lock");
        }
        System.out.println("Overlay pairing session, target, cancellation and timeout checks passed");
    }
    static void await(java.util.concurrent.CountDownLatch latch) {
        try { if (!latch.await(2, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("start timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
