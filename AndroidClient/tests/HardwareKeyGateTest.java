package io.github.n2ray.sill;

public final class HardwareKeyGateTest {
    public static void main(String[] args) {
        assert HardwareKeyGate.sendRemote(true, true, true, false);
        assert !HardwareKeyGate.sendRemote(true, false, true, false);
        assert !HardwareKeyGate.sendRemote(true, true, true, true);
        assert !HardwareKeyGate.sendRemote(true, true, false, false);
        assert !HardwareKeyGate.sendRemote(false, true, true, false);
        System.out.println("Hardware key focus checks passed");
    }
}
