package io.github.n2ray.sall;

public final class AudioRecoveryPolicyTest {
    public static void main(String[] args) {
        AudioRecoveryPolicy policy = new AudioRecoveryPolicy();
        Throwable clock = new IllegalStateException("presentation timestamp unavailable or discontinuous");
        assert !policy.retry(new IllegalStateException("audio underrun"), 0);
        assert policy.retry(clock, 0);
        assert policy.retry(clock, 1_000_000_000L);
        assert !policy.retry(clock, 2_000_000_000L);
        assert policy.retry(clock, 30_000_000_000L);
        assert !policy.retry(new RuntimeException(clock.getMessage()), 31_000_000_000L);
        System.out.println("Audio clock retry is bounded and restricted to presentation failures");
    }
}
