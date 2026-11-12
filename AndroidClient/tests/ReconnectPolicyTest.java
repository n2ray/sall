package io.github.n2ray.sill;

public final class ReconnectPolicyTest {
    public static void main(String[] args) {
        assert ReconnectPolicy.delayMillis(0) == 1000;
        assert ReconnectPolicy.delayMillis(1) == 2000;
        assert ReconnectPolicy.delayMillis(2) == 4000;
        assert ReconnectPolicy.delayMillis(3) == 8000;
        assert ReconnectPolicy.delayMillis(4) == -1;
        assert ReconnectPolicy.delayMillis(-1) == -1;
        assert ReconnectPolicy.retryable(new java.net.SocketException());
        assert ReconnectPolicy.retryable(new java.net.SocketTimeoutException());
        assert ReconnectPolicy.retryable(new java.io.EOFException());
        assert !ReconnectPolicy.retryable(new javax.net.ssl.SSLHandshakeException("pin"));
        assert !ReconnectPolicy.retryable(new java.io.IOException("invalid frame"));
        System.out.println("Reconnect policy checks passed");
    }
}
