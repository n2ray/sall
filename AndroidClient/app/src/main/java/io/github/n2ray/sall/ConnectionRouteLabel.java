package io.github.n2ray.sall;

/** Describes the selected saved address, never the active network path or reachability. */
final class ConnectionRouteLabel {
    static String saved(SavedMacs.Mac mac, SavedMacs.Endpoint endpoint, double now) {
        if (mac.infoIssuedAt() <= 0 || !Double.isFinite(now)) return "已配对";
        String kind = null;
        for (SavedMacs.Advertised address : mac.advertised()) {
            if (!address.endpoint().equals(endpoint)) continue;
            if (kind != null && !kind.equals(address.kind())) return "已配对";
            kind = address.kind();
        }
        for (SavedMacs.Remembered saved : mac.remembered()) {
            double age = now - saved.listedAt();
            if (age < 0 || age > SavedMacs.REMEMBER_FOR || !saved.address().endpoint().equals(endpoint)) continue;
            if (kind != null && !kind.equals(saved.address().kind())) return "已配对";
            kind = saved.address().kind();
        }
        if (kind == null) return "已配对";
        return switch (kind) {
            case "vpn" -> "已配对 · VPN 地址";
            case "lan" -> "已配对 · 局域网地址";
            case "internet" -> "已配对 · 互联网地址";
            default -> "已配对";
        };
    }
    private ConnectionRouteLabel() {}
}
