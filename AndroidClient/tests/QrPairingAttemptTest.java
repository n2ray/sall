package io.github.n2ray.sill;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class QrPairingAttemptTest {
    static final class Transport implements Closeable {
        final AtomicBoolean closed = new AtomicBoolean();
        public void close() { closed.set(true); }
    }
    public static void main(String[] args) throws Exception {
        QrPairingAttempt old = new QrPairingAttempt();
        Transport socket = new Transport(); check(old.track(socket), "first transport accepted");
        old.cancel(); check(socket.closed.get(), "cancel closes current transport");
        Transport late = new Transport(); check(!old.track(late) && late.closed.get(), "late transport closed");
        AtomicInteger saves = new AtomicInteger();
        check(!old.complete(saves::incrementAndGet), "cancelled result cannot save");
        QrPairingAttempt next = new QrPairingAttempt();
        check(next.complete(saves::incrementAndGet) && saves.get() == 1, "fresh generation can save");
        check(!old.complete(saves::incrementAndGet) && saves.get() == 1, "old callback cannot affect new generation");

        QrPairingAttempt racing = new QrPairingAttempt();
        CountDownLatch ready = new CountDownLatch(1), proceed = new CountDownLatch(1);
        Thread worker = new Thread(() -> { ready.countDown(); await(proceed); racing.complete(saves::incrementAndGet); });
        worker.start(); check(ready.await(2, TimeUnit.SECONDS), "worker ready");
        racing.cancel(); proceed.countDown(); worker.join(2000);
        check(!worker.isAlive() && saves.get() == 1, "cancel before queued proof prevents publication");
        for (int i = 0; i < 1000; i++) {
            QrPairingAttempt run = new QrPairingAttempt(); Transport value = new Transport();
            Thread attach = new Thread(() -> run.track(value)); attach.start(); run.cancel(); attach.join();
            check(value.closed.get(), "cancel and late attach cannot leak socket");
        }
        System.out.println("QrPairingAttemptTest passed: cancel, late socket, stale result and concurrent attach");
    }
    static void await(CountDownLatch latch) { try { if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("timeout"); } catch (InterruptedException e) { throw new AssertionError(e); } }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
