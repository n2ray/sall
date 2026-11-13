package io.github.n2ray.sall;

/** Bounds automatic recovery from a broken Android presentation clock. */
final class AudioRecoveryPolicy {
    private final java.util.ArrayDeque<Long> attempts = new java.util.ArrayDeque<>();
    boolean retry(Throwable error, long now) {
        if (!(error instanceof IllegalStateException)
                || error.getMessage() == null || !error.getMessage().startsWith("presentation timestamp unavailable or discontinuous")) return false;
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= 30_000_000_000L) attempts.removeFirst();
        if (attempts.size() >= 2) return false;
        attempts.addLast(now);
        return true;
    }
}
