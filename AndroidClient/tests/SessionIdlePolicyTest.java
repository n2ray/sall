package io.github.n2ray.sall;

public final class SessionIdlePolicyTest {
    public static void main(String[] args) {
        long limit = SessionIdlePolicy.LIMIT_MS;
        assert SessionIdlePolicy.holdUntil(true, true, 10, 10) == 10 + limit;
        assert SessionIdlePolicy.holdUntil(true, true, 10, 10 + limit - 1) == 10 + limit;
        assert SessionIdlePolicy.holdUntil(true, true, 10, 10 + limit) == -1;
        assert SessionIdlePolicy.holdUntil(false, true, 10, 11) == -1;
        assert SessionIdlePolicy.holdUntil(true, false, 10, 11) == -1;
        assert SessionIdlePolicy.holdUntil(true, true, -1, 11) == -1;
        assert SessionIdlePolicy.holdUntil(true, true, 12, 11) == -1;
        assert SessionIdlePolicy.holdUntil(true, true, Long.MAX_VALUE - 1, Long.MAX_VALUE) == Long.MAX_VALUE;
        // Stream restoration and foreground changes do not change the last person action.
        assert SessionIdlePolicy.holdUntil(true, true, 10, 500) == 10 + limit;
        assert SessionIdlePolicy.holdUntil(true, true, 10, 10 + limit + 50) == -1;
        System.out.println("Session idle policy checks passed");
    }
}
