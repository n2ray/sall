package io.github.n2ray.sill;

/** Copied over MacDiscovery only in the isolated no-network probe build. */
final class MacDiscovery {
    interface Listener { void found(String name, String address, int port); void lost(String name); }
    MacDiscovery(android.content.Context context, Listener listener) { }
    void start() { }
    void stop() { }
    java.util.List<PairLink.Address> pairingDoors() { return java.util.List.of(); }
    String recognitionTag(String name) { return null; }
}
