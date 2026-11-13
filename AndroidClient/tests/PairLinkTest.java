package io.github.n2ray.sall;

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
        assert PairLink.pairingDoor(java.util.Set.of("p"));
        assert PairLink.pairingDoor(java.util.Set.of("P"));
        assert !PairLink.pairingDoor(java.util.Set.of("r"));
        PairLink.Address local = new PairLink.Address("192.168.1.9", 17455);
        assert PairLink.parse(base).candidates(java.util.List.of(local)).equals(java.util.List.of(local));
        assert link.candidates(java.util.List.of(local, local)).equals(java.util.List.of(local, new PairLink.Address("100.101.102.103", 7455), new PairLink.Address("fd7a:115c:a1e0::1234", 17455)));
        java.util.List<PairLink.Address> many = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) many.add(new PairLink.Address("192.168.1." + i, 7455));
        assert link.localCandidates(many).size() == 2;
        assert PairLink.parse(base).localCandidates(many).size() == 8;
        PairLink.Address remote = new PairLink.Address("100.101.102.103", 7455);
        assert link.candidates(java.util.List.of(remote)).stream().filter(a -> a.equals(remote)).count() == 2;
        assert link.addresses.get(1).host().equals("fd7a:115c:a1e0::1234") && link.addresses.get(1).port() == 17455;
        byte[] copy = link.secret(); copy[0] = 1; assert link.secret()[0] == 0;
        byte[] nonzero = new byte[16]; java.util.Arrays.fill(nonzero, (byte) 7);
        PairLink retry = PairLink.parse(base.replace("s=" + secret,
                "s=" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonzero)));
        byte[] attempt = retry.secret(); java.util.Arrays.fill(attempt, (byte) 0);
        assert retry.secret()[0] == 7; // Clearing a failed worker's copy preserves the confirmed link for retry.
        retry.clearSecret(); for (byte b : retry.secret()) assert b == 0;
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
