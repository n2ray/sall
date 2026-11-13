package io.github.n2ray.sill;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OverlayPairingDispatchTest {
    public static void main(String[] args) {
        AtomicBoolean ran = new AtomicBoolean(), cleaned = new AtomicBoolean();
        OverlayPairingDispatch.execute(task -> { throw new RejectedExecutionException(); },
                () -> ran.set(true), () -> cleaned.set(true));
        if (ran.get() || !cleaned.get()) throw new AssertionError("rejection must use cleanup path");
        cleaned.set(false);
        OverlayPairingDispatch.execute(Runnable::run, () -> ran.set(true), () -> cleaned.set(true));
        if (!ran.get() || cleaned.get()) throw new AssertionError("accepted work must not use rejection path");
        if (OverlayPairingDispatch.saved(() -> { throw new IllegalStateException("storage failure"); }))
            throw new AssertionError("save exception must become failure");
        if (!OverlayPairingDispatch.saved(() -> true) || OverlayPairingDispatch.saved(() -> false))
            throw new AssertionError("save result must be preserved");
        System.out.println("Overlay dispatch rejection and persistence failure checks passed");
    }
}
