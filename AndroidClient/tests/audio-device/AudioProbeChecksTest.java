package io.github.n2ray.sill;

import java.util.Arrays;

public final class AudioProbeChecksTest {
    private static void require(boolean ok) { if (!ok) throw new AssertionError(); }
    private static AudioProbeChecks.Result pass(String s) { return new AudioProbeChecks.Result(AudioProbeChecks.Status.PASS, s); }

    public static void main(String[] args) {
        final int[] calls = new int[5];
        var badTrack = AudioProbeChecks.run(() -> { calls[0]++; throw new IllegalStateException("track"); },
                () -> { calls[1]++; return pass("480 raw"); }, () -> { calls[2]++; return pass("480 asc"); },
                () -> { calls[3]++; return pass("512 raw"); }, () -> { calls[4]++; return pass("512 asc"); });
        require(!badTrack.passed() && Arrays.stream(calls).allMatch(count -> count == 1));
        require(badTrack.detail().contains("silent AudioTrack FAIL") && badTrack.detail().contains("AAC-ELD 512 ASC PASS"));

        final int[] failed480Calls = new int[5];
        var bad480 = AudioProbeChecks.run(() -> { failed480Calls[0]++; return pass("track"); },
                () -> { failed480Calls[1]++; throw new IllegalArgumentException("480"); },
                () -> { failed480Calls[2]++; return pass("480 asc"); }, () -> { failed480Calls[3]++; return pass("512 raw"); },
                () -> { failed480Calls[4]++; return pass("512 asc"); });
        require(!bad480.passed() && Arrays.stream(failed480Calls).allMatch(count -> count == 1));
        require(bad480.detail().contains("AAC-ELD 480 raw FAIL") && bad480.detail().contains("AAC-ELD 512 ASC PASS"));

        var bad512 = AudioProbeChecks.run(() -> pass("track"), () -> pass("480"), () -> pass("480 asc"),
                () -> { throw new AssertionError("512"); }, () -> pass("512 asc"));
        require(!bad512.passed() && bad512.detail().contains("AAC-ELD 512 raw FAIL"));
        var unsupported = AudioProbeChecks.run(() -> pass("track"),
                () -> new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "not advertised"),
                () -> new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "not advertised"),
                () -> new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "not advertised"),
                () -> new AudioProbeChecks.Result(AudioProbeChecks.Status.UNSUPPORTED, "not advertised"));
        require(unsupported.passed() && unsupported.detail().contains("AAC-ELD 480 raw UNSUPPORTED"));
        var missing = AudioProbeChecks.run(() -> null, () -> pass("480"), () -> pass("480 asc"), () -> pass("512"), () -> pass("512 asc"));
        require(!missing.passed() && missing.detail().contains("silent AudioTrack FAIL"));

        AudioProbeChecks.requirePcmContinuity(true, 2);
        var discontinuity = AudioProbeChecks.run(() -> pass("track"), () -> pass("raw"), () -> {
            AudioProbeChecks.requirePcmContinuity(false, 2); return pass("must not pass");
        }, () -> pass("raw"), () -> pass("asc"));
        require(!discontinuity.passed() && discontinuity.detail().contains("AAC-ELD 480 ASC FAIL")
                && discontinuity.detail().contains("PCM PTS continuity not established (aligned=false, buffers=2)"));
        try {
            AudioProbeChecks.requirePcmContinuity(true, 1);
            throw new AssertionError("one buffer cannot establish continuity");
        } catch (IllegalStateException expected) { require(expected.getMessage().contains("buffers=1")); }

        String trackReport = "legacy=" + "x".repeat(2000) + " production=READY prefill=READY end-of-track";
        var complete = AudioProbeChecks.run(() -> new AudioProbeChecks.Result(AudioProbeChecks.Status.FAIL, trackReport),
                () -> pass("raw 480"), () -> pass("asc 480"), () -> pass("raw 512"), () -> pass("asc 512"));
        require(!complete.passed() && complete.detail().contains(trackReport));

        int[] runtimeCalls = {0, 0, 0, 0};
        var independentRuntime = AudioProbeChecks.runWithRuntime(() -> { throw new IllegalStateException("track"); },
                () -> pass("raw"), () -> pass("asc"), () -> pass("raw"), () -> pass("asc"),
                () -> { runtimeCalls[0]++; throw new IllegalStateException("raw 480"); },
                () -> { runtimeCalls[1]++; return pass("asc 480"); },
                () -> { runtimeCalls[2]++; return pass("raw 512"); },
                () -> { runtimeCalls[3]++; return pass("asc 512"); });
        require(!independentRuntime.passed() && Arrays.stream(runtimeCalls).allMatch(count -> count == 1));
        require(independentRuntime.detail().contains("Runtime 480 raw FAIL")
                && independentRuntime.detail().contains("Runtime 512 ASC PASS"));
        var runtimeFailure = AudioProbeChecks.runWithRuntime(() -> pass("track"), () -> pass("raw"), () -> pass("asc"),
                () -> pass("raw"), () -> pass("asc"), () -> pass("raw"),
                () -> new AudioProbeChecks.Result(AudioProbeChecks.Status.FAIL, "clock not ready"),
                () -> pass("raw"), () -> pass("asc"));
        require(!runtimeFailure.passed() && runtimeFailure.detail().contains("Runtime 480 ASC FAIL: clock not ready"));

        IllegalStateException original = new IllegalStateException("configure failed");
        int[] cleaned = {0};
        AudioProbeChecks.cleanup(original, () -> { cleaned[0]++; throw new IllegalStateException("stop failed"); },
                () -> { cleaned[0]++; throw new IllegalStateException("release failed"); });
        require(cleaned[0] == 2 && original.getSuppressed().length == 2);
        var saved = AudioProbeChecks.run(() -> { throw original; }, () -> pass("480"), () -> pass("480 asc"),
                () -> pass("512"), () -> pass("512 asc"));
        require(!saved.passed() && saved.detail().contains("configure failed")
                && saved.detail().contains("suppressed IllegalStateException: release failed"));
        try {
            AudioProbeChecks.cleanup(null, () -> { throw new IllegalStateException("cleanup failed"); });
            throw new AssertionError("cleanup-only failure was swallowed");
        } catch (IllegalStateException expected) {
            require(expected.getCause() != null && "cleanup failed".equals(expected.getCause().getMessage()));
        }
        System.out.println("Audio device checks execute independently and preserve failure status");
    }
}
