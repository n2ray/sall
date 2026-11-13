package io.github.n2ray.sall;

public final class DiscoveryEpochTest {
    public static void main(String[] args) {
        DiscoveryEpoch epochs = new DiscoveryEpoch();
        long firstRun = epochs.restart();
        DiscoveryEpoch.Ticket first = epochs.found("Mac");
        assert epochs.current(firstRun) && epochs.current("Mac", first);
        epochs.lost("Mac");
        assert !epochs.current("Mac", first);
        DiscoveryEpoch.Ticket replaced = epochs.found("Mac");
        assert !epochs.current("Mac", first) && epochs.current("Mac", replaced);
        DiscoveryEpoch.Ticket other = epochs.found("Other");
        epochs.found("Mac");
        assert !epochs.current("Mac", replaced) && epochs.current("Other", other);
        long secondRun = epochs.restart();
        assert !epochs.current(firstRun) && epochs.current(secondRun) && !epochs.current("Other", other);
        DiscoveryEpoch.Ticket next = epochs.found("Mac");
        assert !epochs.current("Mac", first) && epochs.current("Mac", next);
        System.out.println("Discovery generation checks passed");
    }
}
