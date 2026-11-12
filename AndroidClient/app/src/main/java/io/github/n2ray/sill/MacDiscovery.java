package io.github.n2ray.sill;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Local discovery is a hint only; every connection still requires a paired TLS key. */
final class MacDiscovery {
    interface Listener { void found(String name, String address, int port); void lost(String name); }
    private final NsdManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<NsdServiceInfo> pending = new ArrayDeque<>();
    private final Map<String, PairLink.Address> pairingDoors = new LinkedHashMap<>();
    private final Set<String> present = new HashSet<>();
    private final Listener listener;
    private boolean active, resolving;
    MacDiscovery(Context context, Listener listener) {
        manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE); this.listener = listener;
    }
    private final NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
        public void onStartDiscoveryFailed(String type, int error) { main.post(() -> active = false); }
        public void onStopDiscoveryFailed(String type, int error) { }
        public void onDiscoveryStarted(String type) { }
        public void onDiscoveryStopped(String type) { }
        public void onServiceFound(NsdServiceInfo info) { main.post(() -> { if (active) { present.add(info.getServiceName()); pending.add(info); resolveNext(); } }); }
        public void onServiceLost(NsdServiceInfo info) { main.post(() -> { present.remove(info.getServiceName()); pending.removeIf(p -> p.getServiceName().equals(info.getServiceName())); pairingDoors.remove(info.getServiceName()); listener.lost(info.getServiceName()); }); }
    };
    void start() {
        if (active || manager == null) return;
        active = true;
        try { manager.discoverServices("_sill._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (RuntimeException e) { active = false; }
    }
    void stop() {
        if (!active) return;
        active = false; pending.clear(); pairingDoors.clear(); present.clear();
        try { manager.stopServiceDiscovery(discovery); } catch (IllegalArgumentException ignored) { }
    }
    List<PairLink.Address> pairingDoors() { return List.copyOf(pairingDoors.values()); }
    private void resolveNext() {
        if (!active || resolving || pending.isEmpty()) return;
        resolving = true; NsdServiceInfo service = pending.removeFirst();
        try {
            manager.resolveService(service, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo s, int error) { main.post(() -> { resolving = false; resolveNext(); }); }
                public void onServiceResolved(NsdServiceInfo s) { main.post(() -> {
                    resolving = false;
                    if (active && present.contains(s.getServiceName()) && s.getHost() != null && s.getPort() > 0 && s.getPort() <= 65535) {
                        boolean tls = PairLink.pairingDoor(s.getAttributes().keySet());
                        if (tls) pairingDoors.put(s.getServiceName(), new PairLink.Address(s.getHost().getHostAddress(), s.getPort()));
                        else pairingDoors.remove(s.getServiceName());
                        listener.found(s.getServiceName(), s.getHost().getHostAddress(), s.getPort());
                    }
                    resolveNext();
                }); }
            });
        } catch (RuntimeException e) { resolving = false; resolveNext(); }
    }
}
