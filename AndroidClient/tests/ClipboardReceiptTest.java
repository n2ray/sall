package io.github.n2ray.sill;

public final class ClipboardReceiptTest {
    public static void main(String[] args) {
        ClipboardReceipt r = new ClipboardReceipt();
        byte[] n = {1, 2, 3};
        long a = r.begin(n, 100);
        assert !r.receive(new byte[]{1}, 200, a);
        assert r.receive(n, 200, a);
        assert !r.receive(n, 201, a);
        assert r.take(a) && !r.take(a);
        long b = r.begin(n, 100);
        assert !r.receive(n, 4100, b);
        assert r.expire(b, 4100) && !r.take(b) && !r.receive(n, 4101, b);
        long c = r.begin(n, 100);
        assert r.receive(n, 101, c);
        r.cancel(); assert !r.take(c);
        long d = r.begin(n, 100);
        long e = r.begin(new byte[]{4}, 101);
        assert !r.receive(n, 102, d) && !r.take(d);
        r.cancel(d); assert r.pending(e);
        assert r.receive(new byte[]{4}, 102, e) && r.take(e);
        long chip = r.begin(n, 200);
        assert r.receive(n, 201, chip);
        r.cancel(chip); // a panel opened after ack and before the delayed paste
        assert !r.take(chip);
        long settings = r.begin(n, 300);
        assert r.receive(n, 301, settings);
        r.cancel(chip); // stale chip cancellation cannot revoke a newer settings action
        assert r.take(settings);
        System.out.println("Clipboard receipt checks passed");
    }
}
