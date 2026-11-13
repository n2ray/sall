package io.github.n2ray.sall;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConnectionPreviewPolicyTest {
    private static final Runnable NOOP = () -> {};
    public static void main(String[] args) throws Exception {
        assert ConnectionPreviewPolicy.size(1920, 1080).equals(new ConnectionPreviewPolicy.Size(640, 360));
        assert ConnectionPreviewPolicy.size(1080, 1920).equals(new ConnectionPreviewPolicy.Size(640, 1137));
        assert ConnectionPreviewPolicy.size(200, 100).equals(new ConnectionPreviewPolicy.Size(200, 100));
        assert ConnectionPreviewPolicy.size(1000, .01) == null;
        assert ConnectionPreviewPolicy.size(Double.NaN, 2) == null;
        assert ConnectionPreviewPolicy.size(Double.POSITIVE_INFINITY, 2) == null;
        assert ConnectionPreviewPolicy.size(1, 16_384) == null;
        byte[] pixels = new byte[24]; // two 2-pixel rows with 4 bytes of padding after row one
        Arrays.fill(pixels, (byte) 24);
        pixels[3] = pixels[7] = pixels[15] = pixels[19] = (byte) 255;
        pixels[8] = pixels[9] = pixels[10] = pixels[11] = (byte) 255;
        assert ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.RGBA);
        pixels[14] = 25;
        assert !ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.RGBA);
        assert !ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.BGRA);
        assert ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.ARGB);
        assert ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.ABGR);
        assert ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 12, null);
        assert ConnectionPreviewPolicy.looksBlack(pixels, 2, 2, 7, ConnectionPreviewPolicy.PixelFormat.RGBA);
        assert ConnectionPreviewPolicy.looksBlack(new byte[19], 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.RGBA);
        assert ConnectionPreviewPolicy.looksBlack(null, 2, 2, 12, ConnectionPreviewPolicy.PixelFormat.RGBA);
        assert ConnectionPreviewPolicy.looksBlack(pixels, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ConnectionPreviewPolicy.PixelFormat.RGBA);

        byte[] a = new byte[32], b = new byte[32]; b[31] = 1; // same short prefix, different full identity
        assert ConnectionPreviewPolicy.fingerprintName(a).length() == 64;
        assert !ConnectionPreviewPolicy.fingerprintName(a).equals(ConnectionPreviewPolicy.fingerprintName(b));
        assert ConnectionPreviewPolicy.fingerprintName(new byte[31]) == null;
        ConnectionPreviewPolicy.Guard cloneGuard = new ConnectionPreviewPolicy.Guard();
        byte[] mutable = a.clone(); cloneGuard.bind(9, mutable); mutable[31] = 1;
        cloneGuard.context(true, true, true); assert cloneGuard.begin(9, mutable, true) == null;
        mutable[31] = 0; ConnectionPreviewPolicy.Ticket cloneTicket = cloneGuard.begin(9, mutable, true);
        mutable[31] = 1; assert cloneGuard.publish(cloneTicket, 9, a, true, true, NOOP);
        ConnectionPreviewPolicy.Guard g = new ConnectionPreviewPolicy.Guard();
        g.bind(1, a); g.context(true, true, true);
        assert g.begin(1, a, false) == null && g.begin(1, b, true) == null;
        ConnectionPreviewPolicy.Ticket first = g.begin(1, a, true);
        ConnectionPreviewPolicy.Ticket latest = g.begin(1, a, true);
        assert !g.publish(first, 1, a, true, true, NOOP);
        assert !g.publish(latest, 1, b, true, true, NOOP);
        latest = g.begin(1, a, true);
        assert !g.publish(latest, 1, a, true, false, NOOP); // a black frame never replaces a good preview
        latest = g.begin(1, a, true); g.context(true, true, false);
        g.context(true, true, true); assert !g.publish(latest, 1, a, true, true, NOOP);
        latest = g.begin(1, a, true); g.context(false, true, true);
        g.context(true, true, true); assert !g.publish(latest, 1, a, true, true, NOOP);
        latest = g.begin(1, a, true); g.forget(a, NOOP);
        g.bind(1, a); assert !g.publish(latest, 1, a, true, true, NOOP);
        assert g.begin(1, a, true) == null; // a new binding must re-establish every privacy gate
        g.context(true, true, true); latest = g.begin(1, a, true); g.bind(2, b);
        assert !g.publish(latest, 1, a, true, true, NOOP);
        assert g.begin(2, b, true) == null;
        g.context(true, true, true); latest = g.begin(2, b, true); assert !g.publish(latest, 2, b, false, true, NOOP);
        latest = g.begin(2, b, true);
        AtomicInteger commits = new AtomicInteger();
        assert g.publish(latest, 2, b, true, true, commits::incrementAndGet);
        assert !g.publish(latest, 2, b, true, true, commits::incrementAndGet) && commits.get() == 1;

        // If publication already started, forget waits and then erases it; it cannot be resurrected later.
        ConnectionPreviewPolicy.Ticket racing = g.begin(2, b, true);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean exists = new AtomicBoolean(), published = new AtomicBoolean();
        Thread writer = new Thread(() -> published.set(g.publish(racing, 2, b, true, true, () -> {
            entered.countDown();
            try { if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("release timed out"); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            exists.set(true);
        })));
        writer.start(); assert entered.await(3, TimeUnit.SECONDS);
        Thread forget = new Thread(() -> g.forget(b, () -> exists.set(false)));
        forget.start(); release.countDown(); writer.join(3000); forget.join(3000);
        assert !writer.isAlive() && !forget.isAlive() && published.get() && !exists.get();
        assert !g.publish(racing, 2, b, true, true, () -> exists.set(true));
        assert !exists.get();
        System.out.println("Connection preview scaling, black-frame and generation policy checks passed");
    }
}
