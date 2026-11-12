package io.github.n2ray.sill;

/** Bounded retry only for connection loss, never certificate or protocol failures. */
final class ReconnectPolicy {
    static long delayMillis(int attempt) {
        return attempt >= 0 && attempt < 4 ? 1000L << attempt : -1;
    }
    static boolean retryable(Exception error) {
        return error instanceof java.io.EOFException
                || error instanceof java.net.SocketException
                || error instanceof java.net.SocketTimeoutException
                || error instanceof java.net.UnknownHostException;
    }
}
