package io.github.n2ray.sill;

public final class HardwareKeyFlowTest {
    public static void main(String[] args) {
        HardwareKeyFlow f = new HardwareKeyFlow();
        assert f.down(1, 42, false, true, 0) == HardwareKeyFlow.Action.REMOTE_DOWN;
        assert f.down(1, 42, false, false, 1) == HardwareKeyFlow.Action.IGNORE;
        assert f.up(1) == HardwareKeyFlow.Action.REMOTE_UP;
        assert f.up(1) == HardwareKeyFlow.Action.IGNORE;
        assert f.down(2, 42, false, false, 0) == HardwareKeyFlow.Action.REMOTE_DOWN;
        assert f.down(2, 42, false, true, 1) == HardwareKeyFlow.Action.REMOTE_REPEAT;
        assert f.up(2) == HardwareKeyFlow.Action.REMOTE_UP;
        assert f.down(3, 0xe3, false, false, 0) == HardwareKeyFlow.Action.REMOTE_DOWN;
        assert f.down(3, 0xe3, false, false, 1) == HardwareKeyFlow.Action.IGNORE;
        assert f.down(4, 4, true, true, 0) == HardwareKeyFlow.Action.INPUT_METHOD;
        assert f.down(4, 4, false, false, 1) == HardwareKeyFlow.Action.INPUT_METHOD;
        assert f.up(4) == HardwareKeyFlow.Action.INPUT_METHOD;
        assert f.down(5, 4, false, false, 3) == HardwareKeyFlow.Action.IGNORE;
        assert f.down(6, 4, false, true, 0) == HardwareKeyFlow.Action.REMOTE_DOWN;
        assert f.down(7, 4, false, true, 0) == HardwareKeyFlow.Action.IGNORE;
        assert f.up(6) == HardwareKeyFlow.Action.IGNORE;
        assert f.up(7) == HardwareKeyFlow.Action.REMOTE_UP;
        assert f.release().equals(java.util.List.of(0xe3));
        assert f.up(3) == HardwareKeyFlow.Action.IGNORE;
        System.out.println("Hardware key routing and repeat checks passed");
    }
}
