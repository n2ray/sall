package io.github.n2ray.sall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import java.util.function.Consumer;

/** Owns local mute and the system headphone-disconnect observer for one activity. */
final class LocalAudioControl implements AutoCloseable {
    private final Context context;
    private final AudioMutePolicy policy;
    private final Consumer<Boolean> changed;
    private final Runnable headphonesOut;
    private AudioPlayer player;
    private boolean closed;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
            boolean muted;
            synchronized (LocalAudioControl.this) {
                if (closed || !policy.headphonesRemoved()) return;
                apply(); muted = policy.muted();
            }
            changed.accept(muted);
            headphonesOut.run();
        }
    };

    LocalAudioControl(Context context, boolean initiallyMuted, Consumer<Boolean> changed, Runnable headphonesOut) {
        this.context = context.getApplicationContext();
        this.changed = changed;
        this.headphonesOut = headphonesOut;
        policy = new AudioMutePolicy(initiallyMuted);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) this.context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else this.context.registerReceiver(receiver, filter);
    }

    synchronized boolean muted() { return policy.muted(); }
    synchronized void setPlayer(AudioPlayer player) {
        if (closed) return;
        this.player = player;
        policy.player(player != null);
        apply();
    }
    synchronized void setWatched(boolean watched) { if (!closed) { policy.watched(watched); apply(); } }
    synchronized void received(byte[] packet) {
        if (!closed && packet != null && packet.length > 0 && packet[0] == 2) policy.packet();
    }
    void setMuted(boolean muted) {
        synchronized (this) {
            if (closed || !policy.mute(muted)) return;
            apply();
        }
        changed.accept(muted);
    }
    private void apply() { if (player != null) player.setPlaybackEnabled(policy.audible()); }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true; player = null; policy.player(false);
        context.unregisterReceiver(receiver);
    }
}
