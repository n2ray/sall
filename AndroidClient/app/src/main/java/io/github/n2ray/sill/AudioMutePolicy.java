package io.github.n2ray.sill;

/** Local mute is independent of whether the host is configured to send sound. */
final class AudioMutePolicy {
    private boolean muted, watched, hasPlayer, receivedAudio;

    AudioMutePolicy(boolean muted) { this.muted = muted; }
    boolean muted() { return muted; }
    boolean audible() { return hasPlayer && watched && !muted; }
    void player(boolean present) { hasPlayer = present; receivedAudio = false; }
    void watched(boolean value) { watched = value; }
    void packet() { if (hasPlayer) receivedAudio = true; }
    boolean mute(boolean value) { if (muted == value) return false; muted = value; return true; }
    boolean headphonesRemoved() {
        if (!audible() || !receivedAudio) return false;
        muted = true;
        return true;
    }
}
