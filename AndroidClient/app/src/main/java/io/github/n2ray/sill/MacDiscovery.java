package io.github.n2ray.sill;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;

/** Local discovery is a hint only; every connection still requires a paired TLS key. */
final class MacDiscovery {
    interface Listener { void found(String name, String address, int port); void lost(String name); }
    private final NsdManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<NsdServiceInfo> pending = new ArrayDeque<>();
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
        public void onServiceFound(NsdServiceInfo info) { main.post(() -> { if (active) { pending.add(info); resolveNext(); } }); }
        public void onServiceLost(NsdServiceInfo info) { main.post(() -> { pending.removeIf(p -> p.getServiceName().equals(info.getServiceName())); listener.lost(info.getServiceName()); }); }
    };
    void start() {
        if (active || manager == null) return;
        active = true;
        try { manager.discoverServices("_sill._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (RuntimeException e) { active = false; }
    }
    void stop() {
        if (!active) return;
        active = false; pending.clear();
        try { manager.stopServiceDiscovery(discovery); } catch (IllegalArgumentException ignored) { }
    }
    private void resolveNext() {
        if (!active || resolving || pending.isEmpty()) return;
        resolving = true; NsdServiceInfo service = pending.removeFirst();
        try {
            manager.resolveService(service, new NsdManager.ResolveListener() {
                public void onResolveFailed(NsdServiceInfo s, int error) { main.post(() -> { resolving = false; resolveNext(); }); }
                public void onServiceResolved(NsdServiceInfo s) { main.post(() -> {
                    resolving = false;
                    if (active && s.getHost() != null && s.getPort() > 0) listener.found(s.getServiceName(), s.getHost().getHostAddress(), s.getPort());
                    resolveNext();
                }); }
            });
        } catch (RuntimeException e) { resolving = false; resolveNext(); }
    }
}
