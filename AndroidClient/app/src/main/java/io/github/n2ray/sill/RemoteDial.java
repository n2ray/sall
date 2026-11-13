package io.github.n2ray.sill;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/** One pinned identity per dial, with a deadline independent of DNS or TLS progress. */
final class RemoteDial {
    static final long DEADLINE_MS = 20_000;
    static final int CONNECT_MS = 3_000, HANDSHAKE_MS = 3_000;
    private static final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> { Thread thread = new Thread(task, "sill-dial"); thread.setDaemon(true); return thread; });

    interface Connector<T extends Closeable> { T connect(SavedMacs.Endpoint endpoint, byte[] pin, Attempt attempt) throws Exception; }
    record Connected<T extends Closeable>(SavedMacs.Endpoint endpoint, T value) { }

    static final class Attempt implements Closeable {
        private boolean cancelled;
        private Closeable socket;
        synchronized void attach(Closeable value) throws IOException {
            if (cancelled) { value.close(); throw new CancellationException(); }
            socket = value;
        }
        synchronized void check() { if (cancelled) throw new CancellationException(); }
        synchronized void claim() { check(); socket = null; }
        synchronized void failed() { closeSocket(); }
        @Override public synchronized void close() { cancelled = true; closeSocket(); }
        private void closeSocket() { if (socket != null) { try { socket.close(); } catch (IOException ignored) { } socket = null; } }
    }

    static void closeAfterFailure(Closeable value, Exception failure) {
        try { value.close(); }
        catch (Exception closeError) { if (closeError != failure) failure.addSuppressed(closeError); }
    }

    static List<SavedMacs.Endpoint> order(List<SavedMacs.Mac> records, byte[] pin, SavedMacs.Endpoint requested, boolean preferRequested) {
        if (pin == null || pin.length != 32) throw new IllegalArgumentException("Missing Mac identity");
        List<SavedMacs.Endpoint> addresses = new ArrayList<>(), remembered = new ArrayList<>();
        SavedMacs.Endpoint last = null;
        for (SavedMacs.Mac mac : records) if (java.security.MessageDigest.isEqual(pin, mac.identity().pin())) {
            addresses.addAll(mac.currentAddresses());
            for (SavedMacs.Endpoint endpoint : mac.addresses()) if (!mac.currentAddresses().contains(endpoint)) remembered.add(endpoint);
            last = mac.lastWorked();
        }
        addresses.sort(java.util.Comparator.comparingInt(RemoteDial::rank));
        remembered.sort(java.util.Comparator.comparingInt(RemoteDial::rank));
        if (last != null && addresses.remove(last)) addresses.add(0, last);
        else if (last != null && remembered.remove(last)) remembered.add(0, last);
        if (preferRequested) addresses.add(0, requested);
        addresses.addAll(remembered);
        java.util.Set<String> seen = new java.util.HashSet<>();
        return addresses.stream().filter(e -> seen.add(e.host().toLowerCase(Locale.ROOT) + "|" + e.port())).toList();
    }

    /** The active session's TLS + first-list proof survives address-list refreshes, only for this run. */
    static List<SavedMacs.Endpoint> reconnectOrder(List<SavedMacs.Mac> records, byte[] pin,
                                                   SavedMacs.Endpoint requested, boolean preferRequested,
                                                   SavedMacs.Endpoint verifiedThisRun) {
        List<SavedMacs.Endpoint> candidates = new ArrayList<>(order(records, pin, requested, preferRequested));
        if (verifiedThisRun != null) {
            candidates.removeIf(endpoint -> endpoint.port() == verifiedThisRun.port()
                    && endpoint.host().equalsIgnoreCase(verifiedThisRun.host()));
            candidates.add(0, verifiedThisRun);
        }
        return List.copyOf(candidates);
    }

    private static int rank(SavedMacs.Endpoint endpoint) {
        String host = endpoint.host().toLowerCase(Locale.ROOT);
        if (host.equals("::1") || host.startsWith("127.")) return 0;
        if (host.endsWith(".ts.net") || host.endsWith(".ts.net.")) return 1;
        String[] parts = host.split("\\.");
        if (parts.length == 4) try {
            int first = Integer.parseInt(parts[0]), second = Integer.parseInt(parts[1]);
            if (first == 100 && second >= 64 && second <= 127) return 2;
            if (first == 10 || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168 || first == 169 && second == 254) return 6;
            return 5;
        } catch (NumberFormatException ignored) { }
        if (host.startsWith("fd7a:115c:a1e0:")) return 3;
        // shortcut: shared-subnet priority requires the Android network interface prefixes.
        if (host.endsWith(".local") || host.equals("localhost") || host.startsWith("fd") && host.contains(":") || host.startsWith("fe8") && host.contains(":")) return 6;
        return host.contains(":") ? 5 : 4;
    }

    static <T extends Closeable> Connected<T> connect(List<SavedMacs.Endpoint> addresses, byte[] pin, Attempt attempt,
                                                      Connector<T> connector) throws Exception {
        return connect(addresses, pin, attempt, connector, DEADLINE_MS);
    }

    static <T extends Closeable> Connected<T> connect(List<SavedMacs.Endpoint> addresses, byte[] pin, Attempt attempt,
                                                      Connector<T> connector, long timeoutMs) throws Exception {
        if (pin == null || pin.length != 32 || timeoutMs <= 0) throw new IllegalArgumentException("Invalid dial");
        if (addresses.isEmpty()) throw new IOException("No current addresses for this Mac; select a discovered Mac or enter an address");
        byte[] identity = pin.clone();
        List<SavedMacs.Endpoint> candidates = List.copyOf(addresses);
        Future<Connected<T>> future;
        try {
            future = workers.submit(() -> {
                Exception last = null;
                for (SavedMacs.Endpoint endpoint : candidates) {
                    attempt.check();
                    try {
                        T value = connector.connect(endpoint, identity.clone(), attempt);
                        try { attempt.check(); return new Connected<>(endpoint, value); }
                        catch (CancellationException e) { value.close(); throw e; }
                    } catch (Exception error) {
                        attempt.failed();
                        attempt.check();
                        if (!ReconnectPolicy.retryable(error)) throw error;
                        last = error;
                    }
                }
                throw last;
            });
        } catch (RejectedExecutionException e) { attempt.close(); throw new IOException("Previous address lookup is still finishing", e); }
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        try {
            while (true) {
                attempt.check();
                long remaining = until - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("Mac connection timed out");
                try {
                    Connected<T> result = future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
                    try { attempt.claim(); return result; }
                    catch (CancellationException e) { result.value().close(); throw e; }
                } catch (TimeoutException ignored) { }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception error) throw error;
                    throw new IOException("Mac connection failed", e.getCause());
                }
            }
        } catch (Exception e) { attempt.close(); future.cancel(true); throw e; }
    }
}
