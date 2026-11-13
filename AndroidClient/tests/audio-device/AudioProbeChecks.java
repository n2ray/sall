package io.github.n2ray.sall;

import java.util.IdentityHashMap;

/** Keeps independent device checks independent even when one throws. */
final class AudioProbeChecks {
    enum Status { PASS, FAIL, UNSUPPORTED }
    record Result(Status status, String detail) { }
    record Report(boolean passed, String detail) { }
    interface Check { Result run() throws Throwable; }

    static Report run(Check track, Check raw480, Check asc480, Check raw512, Check asc512) {
        Result t = capture(track), a = capture(raw480), b = capture(asc480), c = capture(raw512), d = capture(asc512);
        return new Report(t.status != Status.FAIL && a.status != Status.FAIL && b.status != Status.FAIL
                && c.status != Status.FAIL && d.status != Status.FAIL,
                "silent AudioTrack " + describe(t) + "; AAC-ELD 480 raw " + describe(a)
                        + "; AAC-ELD 480 ASC " + describe(b) + "; AAC-ELD 512 raw " + describe(c)
                        + "; AAC-ELD 512 ASC " + describe(d));
    }

    static Report runWithRuntime(Check track, Check raw480, Check asc480, Check raw512, Check asc512,
                                 Check runtimeRaw480, Check runtimeAsc480, Check runtimeRaw512, Check runtimeAsc512) {
        Report baseline = run(track, raw480, asc480, raw512, asc512);
        Result a = capture(runtimeRaw480), b = capture(runtimeAsc480), c = capture(runtimeRaw512), d = capture(runtimeAsc512);
        return new Report(baseline.passed && a.status != Status.FAIL && b.status != Status.FAIL
                && c.status != Status.FAIL && d.status != Status.FAIL,
                baseline.detail + "; Runtime 480 raw " + describe(a) + "; Runtime 480 ASC " + describe(b)
                        + "; Runtime 512 raw " + describe(c) + "; Runtime 512 ASC " + describe(d));
    }

    private static Result capture(Check check) {
        try {
            Result result = check.run();
            if (result == null || result.status == null) throw new IllegalStateException("missing check result");
            return result;
        } catch (Throwable failure) {
            return new Result(Status.FAIL, failureDetails(failure));
        }
    }

    private static String describe(Result result) { return result.status + ": " + result.detail; }

    static void requirePcmContinuity(boolean aligned, int outputBuffers) {
        if (!aligned || outputBuffers < 2)
            throw new IllegalStateException("PCM PTS continuity not established (aligned=" + aligned + ", buffers=" + outputBuffers + ")");
    }

    /** Always attempt each cleanup; never replace an existing decoder failure. */
    static void cleanup(Throwable primary, Runnable... operations) {
        Throwable failure = primary;
        for (Runnable operation : operations) {
            try { operation.run(); }
            catch (Throwable next) {
                if (failure == null) failure = next;
                else if (failure != next) failure.addSuppressed(next);
            }
        }
        if (primary == null && failure != null) throw new IllegalStateException("codec cleanup failed", failure);
    }

    private static String failureDetails(Throwable failure) {
        StringBuilder out = new StringBuilder();
        appendFailure(out, failure, "", 0, new IdentityHashMap<>());
        return out.toString();
    }

    private static void appendFailure(StringBuilder out, Throwable failure, String prefix, int depth,
                                      IdentityHashMap<Throwable, Boolean> seen) {
        if (failure == null || depth >= 4 || seen.put(failure, Boolean.TRUE) != null) return;
        if (out.length() != 0) out.append(" | ");
        String message = String.valueOf(failure.getMessage()).replaceAll("[\\r\\n\\t]", " ");
        if (message.length() > 160) message = message.substring(0, 160) + "…";
        out.append(prefix).append(failure.getClass().getSimpleName()).append(": ").append(message);
        StackTraceElement[] stack = failure.getStackTrace();
        for (int i = 0; i < Math.min(stack.length, 6); i++) out.append(" @ ").append(stack[i]);
        appendFailure(out, failure.getCause(), "cause ", depth + 1, seen);
        Throwable[] suppressed = failure.getSuppressed();
        for (int i = 0; i < Math.min(suppressed.length, 2); i++)
            appendFailure(out, suppressed[i], "suppressed ", depth + 1, seen);
    }
}
