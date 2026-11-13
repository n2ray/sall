package io.github.n2ray.sall;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

/** Main-thread adapter; callers report actual person actions separately from connection changes. */
final class SessionScreenAwake implements AutoCloseable {
    private final View view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::update;
    private boolean foreground, streaming, holding;
    private long lastAction = -1;

    SessionScreenAwake(View view) { this.view = view; }

    void setForeground(boolean value) { foreground = value; update(); }
    void setStreaming(boolean value) { streaming = value; update(); }

    void personActed() {
        if (!foreground) return;
        lastAction = SystemClock.uptimeMillis();
        update();
    }

    private void update() {
        handler.removeCallbacks(expire);
        long end = SessionIdlePolicy.holdUntil(foreground, streaming, lastAction, SystemClock.uptimeMillis());
        boolean next = end != SessionIdlePolicy.NO_HOLD;
        if (holding != next) { holding = next; view.setKeepScreenOn(next); }
        if (next) handler.postAtTime(expire, end);
    }

    @Override public void close() {
        foreground = streaming = false; lastAction = -1;
        handler.removeCallbacks(expire);
        if (holding) { holding = false; view.setKeepScreenOn(false); }
    }
}
