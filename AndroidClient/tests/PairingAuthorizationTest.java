package io.github.n2ray.sill;

import java.io.Closeable;
import java.util.concurrent.CancellationException;

public final class PairingAuthorizationTest {
    static final class Resource implements Closeable { boolean closed; public void close() { closed = true; } }
    public static void main(String[] args) throws Exception {
        byte[] saved = new byte[32], other = new byte[32]; other[0] = 1;
        PairingAuthorization known = new PairingAuthorization(saved);
        Resource pending = new Resource(); known.track(pending);
        known.forget(saved).close();
        check(pending.closed, "known same-pin pairing socket closes immediately");
        rejected(() -> known.identify(saved), "late proof cannot restore forgotten identity");

        PairingAuthorization beforeStart = new PairingAuthorization(saved);
        beforeStart.forget(saved);
        rejected(() -> beforeStart.track(new Resource()), "forget before worker starts prevents dialing");

        PairingAuthorization unknown = new PairingAuthorization(null);
        Resource discovery = new Resource(); unknown.track(discovery);
        check(unknown.forget(saved) == null, "unknown peer not assumed to match");
        rejected(() -> unknown.identify(saved), "peer identified after forget is rejected before pair proof");

        PairingAuthorization afterProof = new PairingAuthorization(null);
        afterProof.track(new Resource()); afterProof.identify(saved);
        afterProof.forget(saved);
        rejected(() -> afterProof.identify(saved), "forget after proof but before atomic persistence rejects result");

        PairingAuthorization unrelated = new PairingAuthorization(null);
        unrelated.forget(saved); unrelated.identify(other); unrelated.check();
        PairingAuthorization explicitRetry = new PairingAuthorization(saved);
        explicitRetry.identify(saved); explicitRetry.check();
        System.out.println("PairingAuthorizationTest passed: pending known/unknown peer forget, pre-worker and post-proof races, unrelated new Mac, deliberate new pairing");
    }
    interface Action { void run(); }
    static void rejected(Action action, String message) { try { action.run(); throw new AssertionError(message); } catch (CancellationException expected) {} }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
