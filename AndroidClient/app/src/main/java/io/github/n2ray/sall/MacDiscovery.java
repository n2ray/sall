package io.github.n2ray.sall;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local discovery is an untrusted hint; open-door sessions pin the key they first observe. */
final class MacDiscovery {
    interface Listener { void found(String name, String address, int port); void lost(String name); }
    private final NsdManager manager;
    private final android.net.ConnectivityManager connectivity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private record Pending(NsdServiceInfo service, DiscoveryEpoch.Ticket ticket) { }
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Map<String, PairLink.Address> pairingDoors = new LinkedHashMap<>();
    private final Map<String, String> recognitionTags = new LinkedHashMap<>();
    private final Map<String, HomeDiscoveryMetadata.Door> doors = new LinkedHashMap<>();
    private final Map<String, android.net.Network> networks = new LinkedHashMap<>();
    private final DiscoveryEpoch epochs = new DiscoveryEpoch();
    private final Listener listener;
    private boolean active, resolving;
    private NsdManager.DiscoveryListener discovery;
    MacDiscovery(Context context, Listener listener) {
        manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        connectivity = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE); this.listener = listener;
    }
    private NsdManager.DiscoveryListener listenerFor(long epoch) { return new NsdManager.DiscoveryListener() {
        public void onStartDiscoveryFailed(String type, int error) { main.post(() -> { if (epochs.current(epoch)) { active = false; epochs.restart(); pending.clear(); pairingDoors.clear(); recognitionTags.clear(); doors.clear(); networks.clear(); } }); }
        public void onStopDiscoveryFailed(String type, int error) { }
        public void onDiscoveryStarted(String type) { }
        public void onDiscoveryStopped(String type) { }
        public void onServiceFound(NsdServiceInfo info) { main.post(() -> { if (active && epochs.current(epoch)) { String name = info.getServiceName(); pending.removeIf(p -> p.service().getServiceName().equals(name)); pending.add(new Pending(info, epochs.found(name))); resolveNext(); } }); }
        public void onServiceLost(NsdServiceInfo info) { main.post(() -> { if (!active || !epochs.current(epoch)) return; String name = info.getServiceName(); epochs.lost(name); pending.removeIf(p -> p.service().getServiceName().equals(name)); pairingDoors.remove(name); recognitionTags.remove(name); doors.remove(name); networks.remove(name); listener.lost(name); }); }
    }; }
    void start() {
        if (active || manager == null) return;
        active = true; discovery = listenerFor(epochs.restart());
        try { manager.discoverServices("_sill._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (RuntimeException e) { active = false; }
    }
    void stop() {
        if (!active) return;
        active = false; epochs.restart(); pending.clear(); pairingDoors.clear(); recognitionTags.clear(); doors.clear(); networks.clear();
        try { manager.stopServiceDiscovery(discovery); } catch (IllegalArgumentException ignored) { }
    }
    android.net.Network localNetwork(String name) {
        android.net.Network network = networks.get(name);
        android.net.NetworkCapabilities caps = network == null || connectivity == null ? null : connectivity.getNetworkCapabilities(network);
        if (caps == null || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                || !(caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))) return null;
        return network;
    }
    HomeDiscoveryMetadata.Door door(String name) { return doors.getOrDefault(name, HomeDiscoveryMetadata.Door.UNKNOWN); }
    boolean hasPairingDoor(String name) { return pairingDoors.containsKey(name); }
    List<PairLink.Address> pairingDoors() { return List.copyOf(pairingDoors.values()); }
    String recognitionTag(String name) { return recognitionTags.get(name); }
    private void resolveNext() {
        if (!active || resolving || pending.isEmpty()) return;
        Pending next = pending.removeFirst();
        if (!epochs.current(next.service().getServiceName(), next.ticket())) { resolveNext(); return; }
        resolving = true; NsdServiceInfo service = next.service();
        try {
            manager.resolveService(service, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo s, int error) { main.post(() -> { resolving = false; resolveNext(); }); }
                public void onServiceResolved(NsdServiceInfo s) { main.post(() -> {
                    resolving = false;
                    if (active && service.getServiceName().equals(s.getServiceName()) && epochs.current(service.getServiceName(), next.ticket()) && s.getHost() != null && s.getPort() > 0 && s.getPort() <= 65535) {
                        HomeDiscoveryMetadata metadata = HomeDiscoveryMetadata.parse(s.getAttributes());
                        doors.put(s.getServiceName(), metadata.door());
                        android.net.Network network = android.os.Build.VERSION.SDK_INT >= 33 ? s.getNetwork() : null;
                        if (network == null) networks.remove(s.getServiceName()); else networks.put(s.getServiceName(), network);
                        boolean tls = metadata.door() == HomeDiscoveryMetadata.Door.TLS_OPEN
                                || metadata.door() == HomeDiscoveryMetadata.Door.TLS_PAIRING_REQUIRED;
                        if (tls) pairingDoors.put(s.getServiceName(), new PairLink.Address(s.getHost().getHostAddress(), s.getPort()));
                        else pairingDoors.remove(s.getServiceName());
                        String tag = metadata.recognitionHint();
                        if (tag == null) recognitionTags.remove(s.getServiceName());
                        else recognitionTags.put(s.getServiceName(), tag);
                        listener.found(s.getServiceName(), s.getHost().getHostAddress(), s.getPort());
                    }
                    resolveNext();
                }); }
            });
        } catch (RuntimeException e) { resolving = false; resolveNext(); }
    }
}
