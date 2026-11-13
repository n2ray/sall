package io.github.n2ray.sill;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class RemoteReconnectTest {
    public static void main(String[] args) throws Exception {
        byte[] pin = new byte[32];
        var identity = SavedMacs.Identity.pairingResult(pin, null, null, null);
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            var endpoint = new SavedMacs.Endpoint(server.getInetAddress().getHostAddress(), server.getLocalPort());
            AtomicReference<Throwable> serverError = new AtomicReference<>();
            Thread host = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) try (Socket socket = server.accept()) {
                        StreamWire.write(socket.getOutputStream(), 2, "{\"windows\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        if (i == 1) StreamWire.write(socket.getOutputStream(), 1, new byte[]{7});
                    }
                } catch (Throwable error) { serverError.set(error); }
            });
            host.start();
            RemoteDial.Connector<Socket> connector = (candidate, expectedPin, attempt) -> {
                check(Arrays.equals(expectedPin, pin), "full pin retained across reconnect");
                Socket socket = new Socket(); attempt.attach(socket);
                socket.connect(new InetSocketAddress(candidate.host(), candidate.port()), 1000); socket.setSoTimeout(1000);
                FirstWindowList.await(deadline -> StreamWire.read(socket.getInputStream()), message -> {
                    check(new String(message.payload(), java.nio.charset.StandardCharsets.UTF_8).equals("{\"windows\":[]}"), "first list before success");
                });
                return socket;
            };
            var attempt = new RemoteDial.Attempt();
            var first = RemoteDial.connect(List.of(endpoint), pin, attempt, connector, 1000);
            try { StreamWire.read(first.value().getInputStream()); throw new AssertionError("host must disconnect"); }
            catch (EOFException disconnected) { check(ReconnectPolicy.retryable(disconnected), "real EOF requests retry"); }
            first.value().close(); attempt.close();
            // Reload the zero-candidate state observed after provenance/list refresh. No persisted stale route is revived.
            var refreshed = SavedMacs.read(SavedMacs.encode(List.of(new SavedMacs.Mac(identity, List.of(), endpoint, 2))));
            check(RemoteDial.order(refreshed, pin, endpoint, false).isEmpty(), "reproduce empty refreshed candidate set");
            var routes = RemoteDial.reconnectOrder(refreshed, pin, endpoint, false, first.endpoint());
            check(routes.equals(List.of(endpoint)), "only current run's verified winner retained");
            var second = RemoteDial.connect(routes, pin, new RemoteDial.Attempt(), connector, 1000);
            check(StreamWire.read(second.value().getInputStream()).payload()[0] == 7, "real second connection receives frame");
            second.value().close(); host.join(1500);
            check(!host.isAlive() && serverError.get() == null, "host completed both connections");
            check(RemoteDial.reconnectOrder(refreshed, pin, endpoint, false, null).isEmpty(), "fresh app run cannot restore stale route");
        }
        for (String reason : List.of("removed", "remoteOff")) {
            var outcome = GoodbyePolicy.outcome(new GoodbyePolicy.Goodbye(reason, null, null, null), "Mac", true);
            check(!outcome.reconnect() || !outcome.remoteAllowed(), "terminal/access-off stops outer retry before runtime fallback: " + reason);
        }
        var endpoint = new SavedMacs.Endpoint("localhost", 7455);
        try { RemoteDial.connect(List.of(endpoint), pin, new RemoteDial.Attempt(), (e,p,a) -> { Thread.sleep(200); return new RemoteDialTest.Resource(); }, 20); throw new AssertionError(); }
        catch (SocketTimeoutException expected) { }
        var fresh = RemoteDial.connect(List.of(endpoint), pin, new RemoteDial.Attempt(), (e,p,a) -> { Thread.sleep(60); return new RemoteDialTest.Resource(); }, 500);
        fresh.value().close();
        System.out.println("RemoteReconnectTest passed: real TCP EOF/reconnect/frame, empty refreshed routes, per-run proof, fresh deadline, revoked/access-off stop");
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
