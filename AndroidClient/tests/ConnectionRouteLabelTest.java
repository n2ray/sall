package io.github.n2ray.sall;

import java.util.List;

public final class ConnectionRouteLabelTest {
    public static void main(String[] args) {
        SavedMacs.Identity id = SavedMacs.Identity.migrated(new byte[32]);
        SavedMacs.Endpoint vpn = new SavedMacs.Endpoint("100.64.1.2", 7455);
        SavedMacs.Endpoint local = new SavedMacs.Endpoint("192.168.1.5", 7455);
        SavedMacs.Endpoint typed = new SavedMacs.Endpoint("example.test", 7455);
        SavedMacs.Advertised advertised = new SavedMacs.Advertised(vpn, "vpn");
        SavedMacs.Mac signed = new SavedMacs.Mac(id, List.of(typed), vpn, 100,
                List.of(local), List.of(advertised), List.of(new SavedMacs.Remembered(new SavedMacs.Advertised(local, "vpn"), 90)), 100);
        check(ConnectionRouteLabel.saved(signed, vpn, 110).equals("已配对 · VPN 地址"));
        check(ConnectionRouteLabel.saved(signed, typed, 110).equals("已配对"));
        check(ConnectionRouteLabel.saved(signed, local, 110).equals("已配对 · VPN 地址"));
        check(ConnectionRouteLabel.saved(signed, local, 90 + SavedMacs.REMEMBER_FOR + 1).equals("已配对"));
        SavedMacs.Mac legacy = new SavedMacs.Mac(id, List.of(vpn), vpn, 0,
                List.of(), List.of(advertised), List.of(), 0);
        check(ConnectionRouteLabel.saved(legacy, vpn, 110).equals("已配对"));
        SavedMacs.Mac conflict = new SavedMacs.Mac(id, List.of(), vpn, 100, List.of(),
                List.of(advertised, new SavedMacs.Advertised(vpn, "lan")), List.of(), 100);
        check(ConnectionRouteLabel.saved(conflict, vpn, 110).equals("已配对"));
        SavedMacs.Mac lan = new SavedMacs.Mac(id, List.of(), local, 100, List.of(),
                List.of(new SavedMacs.Advertised(local, "lan")), List.of(), 100);
        check(ConnectionRouteLabel.saved(lan, local, 110).equals("已配对 · 局域网地址"));
        SavedMacs.Mac internet = new SavedMacs.Mac(id, List.of(), typed, 100, List.of(),
                List.of(new SavedMacs.Advertised(typed, "internet")), List.of(), 100);
        check(ConnectionRouteLabel.saved(internet, typed, 110).equals("已配对 · 互联网地址"));
        SavedMacs.Mac unknown = new SavedMacs.Mac(id, List.of(), vpn, 100, List.of(),
                List.of(new SavedMacs.Advertised(vpn, "unknown")), List.of(), 100);
        check(ConnectionRouteLabel.saved(unknown, vpn, 110).equals("已配对"));
        SavedMacs.Mac crossSourceConflict = new SavedMacs.Mac(id, List.of(), vpn, 100, List.of(),
                List.of(advertised), List.of(new SavedMacs.Remembered(new SavedMacs.Advertised(vpn, "lan"), 105)), 100);
        check(ConnectionRouteLabel.saved(crossSourceConflict, vpn, 110).equals("已配对"));
        SavedMacs.Mac future = new SavedMacs.Mac(id, List.of(), local, 100, List.of(), List.of(),
                List.of(new SavedMacs.Remembered(new SavedMacs.Advertised(local, "vpn"), 111)), 100);
        check(ConnectionRouteLabel.saved(future, local, 110).equals("已配对"));
        check(ConnectionRouteLabel.saved(signed, vpn, Double.NaN).equals("已配对"));
        check(ConnectionRouteLabel.saved(signed, vpn, Double.POSITIVE_INFINITY).equals("已配对"));
        check(ConnectionRouteLabel.saved(signed, vpn, Double.NEGATIVE_INFINITY).equals("已配对"));
        System.out.println("Connection route labels use signed address origin only");
    }
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
}
