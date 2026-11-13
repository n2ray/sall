package io.github.n2ray.sall;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/** Turns worker rejection and persistence errors into the caller's retry path. */
final class OverlayPairingDispatch {
    static void execute(Executor executor, Runnable work, Runnable rejected) {
        try { executor.execute(work); } catch (RejectedExecutionException busy) { rejected.run(); }
    }
    static boolean saved(BooleanSupplier save) {
        try { return save.getAsBoolean(); } catch (RuntimeException failed) { return false; }
    }
    private OverlayPairingDispatch() {}
}
