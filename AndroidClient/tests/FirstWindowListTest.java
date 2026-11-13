package io.github.n2ray.sall;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class FirstWindowListTest {
    static StreamWire.Message message(int kind) { return new StreamWire.Message(kind, 0, false, new byte[0]); }
    public static void main(String[] args) throws Exception {
        var source = List.of(message(18), message(0), message(2)).iterator();
        AtomicInteger validated = new AtomicInteger();
        var buffered = FirstWindowList.await(deadline -> {
            check(deadline > System.nanoTime(), "absolute deadline supplied to transport"); return source.next();
        }, value -> validated.incrementAndGet());
        check(buffered.size() == 3 && validated.get() == 1, "metadata retained until validated list");
        try { FirstWindowList.await(deadline -> message(2), value -> { throw new IOException("invalid list"); }); throw new AssertionError(); }
        catch (IOException expected) { check(expected.getMessage().equals("invalid list"), "invalid list rejected"); }
        try { FirstWindowList.await(deadline -> message(18), value -> {}); throw new AssertionError(); }
        catch (IOException expected) { check(expected.getMessage().contains("Too many"), "unbounded pre-list traffic rejected"); }
        try { FirstWindowList.await(deadline -> message(22), value -> { throw new GoodbyePolicy.Ended(GoodbyePolicy.outcome(new GoodbyePolicy.Goodbye("removed",null,null,null),"Mac",true)); }); throw new AssertionError(); }
        catch (GoodbyePolicy.Ended expected) { check(!expected.outcome.reconnect(), "goodbye preserved"); }
        AtomicInteger routes = new AtomicInteger();
        byte[] pin = new byte[32];
        var first = new SavedMacs.Endpoint("first.example",7455); var next = new SavedMacs.Endpoint("next.example",7455);
        var result = RemoteDial.connect(List.of(first,next),pin,new RemoteDial.Attempt(),(endpoint, identity, attempt) -> {
            routes.incrementAndGet();
            FirstWindowList.await(deadline -> { if (endpoint.equals(first)) throw new SocketTimeoutException("no list"); return message(2); }, value -> {});
            return new RemoteDialTest.Resource();
        });
        check(result.endpoint().equals(next) && routes.get() == 2, "first-list timeout falls back before success");
        result.value().close();
        System.out.println("FirstWindowListTest passed: validation, buffering, flood limit, goodbye, first-list timeout fallback");
    }
    static void check(boolean value, String text) { if (!value) throw new AssertionError(text); }
}
