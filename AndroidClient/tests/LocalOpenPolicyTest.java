package io.github.n2ray.sall;

public final class LocalOpenPolicyTest {
    public static void main(String[] args) throws Exception {
        var open = HomeDiscoveryMetadata.Door.TLS_OPEN;
        assert LocalOpenPolicy.allowed(open, "192.168.1.3");
        assert LocalOpenPolicy.allowed(open, "10.1.2.3");
        assert LocalOpenPolicy.allowed(open, "172.16.2.3");
        assert LocalOpenPolicy.allowed(open, "fd00::1");
        assert !LocalOpenPolicy.allowed(open, "127.0.0.1");
        assert !LocalOpenPolicy.allowed(open, "100.64.0.1");
        assert !LocalOpenPolicy.allowed(open, "fd7a:115c:a1e0::1234");
        assert !LocalOpenPolicy.allowed(open, "8.8.8.8");
        assert !LocalOpenPolicy.allowed(open, "example.local");
        assert !LocalOpenPolicy.allowed(HomeDiscoveryMetadata.Door.TLS_PAIRING_REQUIRED, "192.168.1.3");
        assert !LocalOpenPolicy.allowed(HomeDiscoveryMetadata.Door.LEGACY_PLAIN, "192.168.1.3");
        assert !LocalOpenPolicy.allowed(HomeDiscoveryMetadata.Door.UNKNOWN, "192.168.1.3");
        byte[] original = new byte[32], other = new byte[32]; other[0] = 1;
        byte[] bound = LocalOpenPolicy.bind(original, null); original[0] = 2;
        assert bound[0] == 0;
        assert LocalOpenPolicy.bind(bound, bound.clone()).length == 32;
        try { LocalOpenPolicy.bind(bound, other); throw new AssertionError("known identity changed"); }
        catch (java.io.IOException expected) { }
        try { LocalOpenPolicy.bind(new byte[31], null); throw new AssertionError("invalid identity"); }
        catch (java.io.IOException expected) { }
        System.out.println("Unpaired home entry uses local TLS and pins its observed identity");
    }
}
