package io.github.n2ray.sill;

public final class AudioMutePolicyTest {
    public static void main(String[] args) {
        AudioMutePolicy p = new AudioMutePolicy(false);
        p.watched(true); p.player(true);
        assert p.audible();
        assert !p.headphonesRemoved(); // Do not mute merely because an output disappears before audio arrives.
        p.packet();
        boolean labelWasMuted = p.muted();
        assert p.headphonesRemoved();
        assert p.muted() && !p.audible();
        assert !p.mute(!labelWasMuted) && p.muted(); // A tap on the stale mute label cannot unmute.
        assert !p.headphonesRemoved();
        assert p.mute(false) && p.audible();
        p.watched(false);
        assert !p.headphonesRemoved() && !p.muted();
        p.watched(true); p.player(false); p.player(true);
        assert !p.headphonesRemoved(); // A new session has not received sound yet.
        p.packet(); p.mute(true); p.watched(false); p.watched(true);
        assert !p.audible(); // Returning from PiP or background never unmutes locally.
        p.mute(false); assert p.audible();
        System.out.println("Local audio mute policy checks passed");
    }
}
