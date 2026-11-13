package io.github.n2ray.sall;

import java.util.HashMap;
import java.util.Map;

/** Invalidates pending NSD work when discovery restarts or a service disappears. */
final class DiscoveryEpoch {
    record Ticket(long run, long service) { }
    private long run, nextService;
    private final Map<String, Long> services = new HashMap<>();

    long restart() { services.clear(); return ++run; }
    boolean current(long candidate) { return run == candidate; }
    Ticket found(String name) {
        long service = ++nextService;
        services.put(name, service);
        return new Ticket(run, service);
    }
    void lost(String name) { services.remove(name); }
    boolean current(String name, Ticket ticket) {
        return ticket.run() == run && Long.valueOf(ticket.service()).equals(services.get(name));
    }
}
