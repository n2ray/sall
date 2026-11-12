package io.github.n2ray.sill;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Rational;
import android.view.View;

/** Android system PiP controls; UI calls are on the activity thread. */
final class PipController implements AutoCloseable {
    interface Listener {
        boolean eligible();
        void changed();
        void ending();
    }
    private final Activity activity;
    private final View picture;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PipPolicy state = new PipPolicy();
    private final String action;
    private int width = 16, height = 10;
    private boolean ending;
    private final Runnable expiry = () -> { if (state.pauseExpired(SystemClock.uptimeMillis())) stop(); };
    private final BroadcastReceiver controls = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!action.equals(intent.getAction()) || !state.active()) return;
            state.setPlaying(state.paused(), SystemClock.uptimeMillis());
            handler.removeCallbacks(expiry);
            if (state.paused()) handler.postDelayed(expiry, PipPolicy.PAUSE_LIMIT_MS);
            updateParams(); listener.changed();
        }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (state.active()) stop(); }
    };

    PipController(Activity activity, View picture, Listener listener) {
        this.activity = activity; this.picture = picture; this.listener = listener;
        action = activity.getPackageName() + ".PIP_CONTROL";
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(controls, filter, Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(controls, filter, activity.getPackageName() + ".permission.PIP_CONTROL", null);
        activity.registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }
    boolean supported() { return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE); }
    boolean active() { return state.active(); }
    boolean paused() { return state.paused(); }
    boolean watched(boolean foreground) { return state.watched(foreground); }
    boolean deviceLocked() {
        android.app.KeyguardManager manager = (android.app.KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        return manager != null && manager.isKeyguardLocked();
    }
    void dimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (this.width == width && this.height == height)) return;
        this.width = width; this.height = height;
        if (state.active()) updateParams();
    }
    void leave() {
        if (state.active() || !supported() || deviceLocked() || !listener.eligible()) return;
        PictureInPictureParams params = parameters();
        state.started(); listener.changed();
        try {
            if (!activity.enterPictureInPictureMode(params)) { state.ended(); listener.changed(); }
        } catch (IllegalStateException | IllegalArgumentException e) {
            state.ended(); listener.changed();
        }
    }
    void modeChanged(boolean active) {
        if (ending) return;
        if (active) {
            state.started();
            if (deviceLocked() || !listener.eligible()) { stop(); return; }
        } else { state.ended(); handler.removeCallbacks(expiry); }
        listener.changed();
    }
    void validate() { if (state.active() && (deviceLocked() || !listener.eligible())) stop(); }
    void stop() {
        if (!state.active() || ending) return;
        ending = true; state.ended(); handler.removeCallbacks(expiry);
        listener.ending();
        // Android has no stop-PiP API: ending this activity removes its system PiP window.
        activity.finishAndRemoveTask();
    }
    private PictureInPictureParams parameters() {
        PipPolicy.Aspect ratio = PipPolicy.aspect(width, height);
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(ratio.width(), ratio.height()));
        Rect source = new Rect(); if (picture.getGlobalVisibleRect(source)) builder.setSourceRectHint(source);
        boolean paused = state.paused();
        PendingIntent intent = PendingIntent.getBroadcast(activity, 0,
                new Intent(action).setPackage(activity.getPackageName()), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String label = paused ? "继续播放" : "暂停";
        builder.setActions(java.util.List.of(new RemoteAction(Icon.createWithResource(activity,
                paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause), label, label, intent)));
        if (Build.VERSION.SDK_INT >= 31) builder.setSeamlessResizeEnabled(true);
        return builder.build();
    }
    private void updateParams() { if (state.active()) activity.setPictureInPictureParams(parameters()); }
    @Override public void close() {
        state.ended(); handler.removeCallbacks(expiry);
        activity.unregisterReceiver(controls); activity.unregisterReceiver(screenOff);
    }
}
