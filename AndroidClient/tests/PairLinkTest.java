package io.github.n2ray.sill;

import java.util.Base64;

public final class PairLinkTest {
    private static void reject(String link) {
        try { PairLink.parse(link); throw new AssertionError("accepted invalid link"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        byte[] fp = new byte[32];
        byte[] prefix = {(byte)0x50, (byte)0xd8, 0x58, (byte)0xe0, (byte)0x98, 0x5e, (byte)0xcc, 0x7f, 0x60, 0x41};
        System.arraycopy(prefix, 0, fp, 0, prefix.length);
        String k = Base64.getUrlEncoder().withoutPadding().encodeToString(fp);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        String base = "sill://pair?v=1&m=A3C5HR4RBV67YR21&k=" + k + "&s=" + secret + "&p=7455";
        PairLink link = PairLink.parse(base + "&n=Tom%20%26%20Jerry%E2%80%99s%20Mac%3Dmini%3F&a=100.101.102.103&a=%5Bfd7a%3A115c%3Aa1e0%3A%3A1234%5D%3A17455");
        assert link.name.equals("Tom & Jerry’s Mac=mini?");
        assert link.port == 7455 && link.addresses.size() == 2;
        assert link.addresses.get(1).host().equals("fd7a:115c:a1e0::1234") && link.addresses.get(1).port() == 17455;
        byte[] copy = link.secret(); copy[0] = 1; assert link.secret()[0] == 0;
        assert PairLink.parse(base).name.equals("Mac");
        assert PairLink.parse(base + "&n=Bad%E2%80%AE%20Mac").name.equals("Bad Mac");
        assert PairLink.parse(base + "&a=10.0.0.1&a=10.0.0.2&a=10.0.0.3&a=10.0.0.4&a=10.0.0.5&a=10.0.0.6").addresses.size() == 5;
        reject(base + "&v=1"); reject(base + "&k=" + k); reject(base.replace("A3C5", "a3c5"));
        reject(base.replace("A3C5", "B3C5")); reject(base.replace("p=7455", "p=0"));
        reject(base.replace("p=7455", "p=65536")); reject(base.replace("s=" + secret, "s=" + secret + "="));
        reject(base + "&a=1.2.3"); reject(base + "&a=fe80%3A%3A1%25en0");
        reject(base + "&a=10.0.0.1&a=10.0.0.2&a=10.0.0.3&a=10.0.0.4&a=10.0.0.5&a=1.2.3");
        reject(base.replace("sill://pair", "http://pair")); reject(base.replace("sill://pair", "sill://evil"));
        reject(base + "&a=0127.0.0.1"); reject(base + "&a=0x7f.0.0.1");
        link.clearSecret(); for (byte b : link.secret()) assert b == 0;
        System.out.println("PairLinkTest passed");
    }
}
