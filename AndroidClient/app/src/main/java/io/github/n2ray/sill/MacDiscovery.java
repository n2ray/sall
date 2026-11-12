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

/** Local discovery is a hint only; every connection still requires a paired TLS key. */
final class MacDiscovery {
    interface Listener { void found(String name, String address, int port); void lost(String name); }
    private final NsdManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private record Pending(NsdServiceInfo service, DiscoveryEpoch.Ticket ticket) { }
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Map<String, PairLink.Address> pairingDoors = new LinkedHashMap<>();
    private final Map<String, String> recognitionTags = new LinkedHashMap<>();
    private final DiscoveryEpoch epochs = new DiscoveryEpoch();
    private final Listener listener;
    private boolean active, resolving;
    private NsdManager.DiscoveryListener discovery;
    MacDiscovery(Context context, Listener listener) {
        manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE); this.listener = listener;
    }
    private NsdManager.DiscoveryListener listenerFor(long epoch) { return new NsdManager.DiscoveryListener() {
        public void onStartDiscoveryFailed(String type, int error) { main.post(() -> { if (epochs.current(epoch)) { active = false; epochs.restart(); pending.clear(); pairingDoors.clear(); recognitionTags.clear(); } }); }
        public void onStopDiscoveryFailed(String type, int error) { }
        public void onDiscoveryStarted(String type) { }
        public void onDiscoveryStopped(String type) { }
        public void onServiceFound(NsdServiceInfo info) { main.post(() -> { if (active && epochs.current(epoch)) { String name = info.getServiceName(); pending.removeIf(p -> p.service().getServiceName().equals(name)); pending.add(new Pending(info, epochs.found(name))); resolveNext(); } }); }
        public void onServiceLost(NsdServiceInfo info) { main.post(() -> { if (!active || !epochs.current(epoch)) return; String name = info.getServiceName(); epochs.lost(name); pending.removeIf(p -> p.service().getServiceName().equals(name)); pairingDoors.remove(name); recognitionTags.remove(name); listener.lost(name); }); }
    }; }
    void start() {
        if (active || manager == null) return;
        active = true; discovery = listenerFor(epochs.restart());
        try { manager.discoverServices("_sill._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery); }
        catch (RuntimeException e) { active = false; }
    }
    void stop() {
        if (!active) return;
        active = false; epochs.restart(); pending.clear(); pairingDoors.clear(); recognitionTags.clear();
        try { manager.stopServiceDiscovery(discovery); } catch (IllegalArgumentException ignored) { }
    }
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
                        boolean tls = PairLink.pairingDoor(s.getAttributes().keySet());
                        if (tls) pairingDoors.put(s.getServiceName(), new PairLink.Address(s.getHost().getHostAddress(), s.getPort()));
                        else pairingDoors.remove(s.getServiceName());
                        String tag = RecognitionTag.fromTxt(s.getAttributes().get("r"));
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
