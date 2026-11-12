package io.github.n2ray.sill;

public final class HostLockStateTest {
    public static void main(String[] args) {
        HostLockState state = new HostLockState(false);
        assert !state.onWindowList(false, true, true, 0); // old/inconsistent host: no locked field
        assert state.reason() == HostLockState.Reason.NONE && state.maySendControl();
        assert !state.onWindowList(true, false, false, 1);
        assert state.reason() == HostLockState.Reason.SCREEN_LOCKED && state.shade() == HostLockState.Shade.NONE;
        assert state.maySendControl() && state.shouldDecode(false);

        assert state.onWindowList(true, true, false, 2);
        assert !state.onWindowList(true, true, false, 3); // clear only once
        assert state.shade() == HostLockState.Shade.OTHER_USER && !state.maySendControl();
        assert !state.shouldDecode(true);
        state.onKeyframePresented(state.generation());
        assert state.needsFreshFrame() && state.shade() == HostLockState.Shade.OTHER_USER;
        assert !state.onWindowList(false, false, false, 4);
        assert state.shade() == HostLockState.Shade.UNLOCKING && !state.shouldDecode(false) && state.shouldDecode(true);
        assert !state.shouldDecode(false, state.generation() - 1) && state.shouldDecode(false, state.generation());
        state.onTime(4003);
        assert state.shade() == HostLockState.Shade.UNLOCKING;
        state.onTime(4004);
        assert state.shade() == HostLockState.Shade.NONE && state.needsFreshFrame();
        state.onKeyframePresented(state.generation());
        assert !state.needsFreshFrame() && state.shouldDecode(false);

        assert state.onWindowList(true, true, true, 5000); // never trust unadvertised view
        assert state.isCovered() && !state.viewOnly();
        assert !state.onWindowList(true, false, false, 6000);
        assert state.reason() == HostLockState.Reason.SCREEN_LOCKED && state.shade() == HostLockState.Shade.UNLOCKING;
        state.onKeyframePresented(state.generation());
        assert state.shade() == HostLockState.Shade.NONE && state.maySendControl();

        HostLockState view = new HostLockState(true);
        assert !view.onWindowList(true, true, true, 0);
        assert view.viewOnly() && !view.isCovered() && !view.maySendControl();
        assert view.onWindowList(true, true, false, 1);
        assert view.isCovered();
        assert !view.onWindowList(true, true, true, 2);
        assert view.viewOnly() && view.needsFreshFrame() && !view.shouldDecode(false);
        view.onKeyframePresented(view.generation());
        assert view.shade() == HostLockState.Shade.NONE && view.shouldDecode(false) && !view.maySendControl();
        long old = view.generation();
        assert view.onWindowList(true, true, false, 3);
        assert !view.onWindowList(false, false, false, 4);
        view.onKeyframePresented(old);
        assert view.needsFreshFrame() && view.shade() == HostLockState.Shade.UNLOCKING;
        System.out.println("Host lock state checks passed");
    }
}
