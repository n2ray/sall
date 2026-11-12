package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/** Relative pointer and two-finger scroll in the same normalized coordinates as Sill. */
final class TrackpadView extends View {
    interface Listener {
        void pointer(String action, float x, float y);
        void scroll(String phase, float x, float y, float dx, float dy);
        default void gesture(String name, int fingers) { }
    }
    private final Listener listener;
    private final Paint paint = new Paint(3);
    private float x = .5f, y = .5f, lastX, lastY, startX, startY;
    private long downAt, lastScrollAt, momentumAt;
    private boolean moved, twoFinger, scrolling, dragging, touchActive, touchOwner, silenced, reversed, coasting, pictureBusy;
    private int scrollSign = 1;
    private float momentumX, momentumY, momentumWidth;
    private VelocityTracker velocity;
    private Runnable beforeTwoFinger;
    private final ScrollMomentum momentum = new ScrollMomentum();
    private final MultiFingerGesture multiFinger = new MultiFingerGesture();
    private boolean macGesturesEnabled;
    private final Runnable longPress;
    private final Runnable coast = new Runnable() {
        @Override public void run() {
            if (!coasting) return;
            long now = android.os.SystemClock.uptimeMillis();
            double dt = (now - momentumAt) / 1000d;
            if (dt <= 0) { postDelayed(this, 16); return; }
            momentumAt = now;
            if (!momentum.advance(dt, getResources().getDisplayMetrics().density)) { stopMomentum(); return; }
            listener.scroll(null, momentumX, momentumY, (float) momentum.stepX(dt) / momentumWidth,
                    (float) momentum.stepY(dt) / (momentumWidth / 1.6f));
            postDelayed(this, 16);
        }
    };

    TrackpadView(Context context, Listener listener) {
        super(context); this.listener = listener;
        longPress = () -> {
            if (!moved && !twoFinger) {
                dragging = true;
                this.listener.pointer("leftDown", x, y);
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
        };
        setContentDescription("触控板：滑动移动指针，轻点点击，双指滚动或右键，长按拖动");
        setFocusable(true);
    }
    float pointerX() { return x; }
    float pointerY() { return y; }
    boolean busyForPicture() { return touchActive && touchOwner; }
    boolean beginPicture() { if (touchActive) return false; pictureBusy = true; stopMomentum(); return true; }
    void endPicture() { pictureBusy = false; }
    void setBeforeTwoFinger(Runnable action) { beforeTwoFinger = action; }
    void position(float x, float y) { this.x = clamp(x); this.y = clamp(y); }
    void setReverseScrolling(boolean reverse) { reversed = reverse; }
    void setMacGesturesEnabled(boolean enabled) { macGesturesEnabled = enabled; }
    void cancelGestures() {
        removeCallbacks(longPress);
        if (scrolling) listener.scroll("ended", x, y, 0, 0);
        if (dragging) listener.pointer("leftUp", x, y);
        scrolling = dragging = touchActive = silenced = false;
        stopMomentum(); recycleVelocity(); multiFinger.cancel();
    }
    private void recycleVelocity() { if (velocity != null) { velocity.recycle(); velocity = null; } }
    private void stopMomentum() {
        removeCallbacks(coast); momentum.clear();
        if (coasting) { coasting = false; listener.scroll("momentumEnded", momentumX, momentumY, 0, 0); }
    }
    private void startMomentum(MotionEvent event, float areaWidth) {
        if (velocity == null || event.getPointerCount() < 2 || event.getEventTime() - lastScrollAt > 80) return;
        velocity.computeCurrentVelocity(1000);
        int first = event.getPointerId(0), second = event.getPointerId(1);
        float vx = (velocity.getXVelocity(first) + velocity.getXVelocity(second)) / 2 * scrollSign;
        float vy = (velocity.getYVelocity(first) + velocity.getYVelocity(second)) / 2 * scrollSign;
        if (!momentum.start(vx, vy, getResources().getDisplayMetrics().density)) return;
        momentumX = x; momentumY = y; momentumWidth = Math.max(1, areaWidth); coasting = true;
        listener.scroll("momentumBegan", momentumX, momentumY, 0, 0);
        listener.scroll(null, momentumX, momentumY, (float) momentum.firstX() / momentumWidth,
                (float) momentum.firstY() / (momentumWidth / 1.6f));
        momentumAt = android.os.SystemClock.uptimeMillis(); postDelayed(coast, 16);
    }
    private static float clamp(float n) { return Math.max(0, Math.min(1, n)); }
    private float midX(MotionEvent e) { return (e.getX(0) + e.getX(1)) / 2; }
    private float midY(MotionEvent e) { return (e.getY(0) + e.getY(1)) / 2; }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(30, 32, 36));
        paint.setColor(0x18ffffff);
        float step = getResources().getDisplayMetrics().density * 18;
        for (float px = step / 2; px < getWidth(); px += step)
            for (float py = step / 2; py < getHeight(); py += step) canvas.drawCircle(px, py, step / 24, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        return touch(event, getWidth(), true);
    }
    boolean touch(MotionEvent event, float areaWidth, boolean allowLongPress) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (allowLongPress && pictureBusy) return true;
            if (touchActive) return true;
            touchActive = true; touchOwner = allowLongPress;
            recycleVelocity(); velocity = VelocityTracker.obtain();
        }
        if (!touchActive || touchOwner != allowLongPress) return true;
        if (velocity != null) velocity.addMovement(event);
        float density = getResources().getDisplayMetrics().density;
        if (allowLongPress) {
            int action = event.getActionMasked(), index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                boolean justSilenced = multiFinger.down(event.getPointerId(index), event.getX(index) / density,
                        event.getY(index) / density, event.getEventTime(), dragging);
                if (justSilenced) {
                    if (scrolling) listener.scroll("ended", x, y, 0, 0);
                    scrolling = false; silenced = moved = true; stopMomentum();
                }
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
                for (int i = 0; i < event.getPointerCount(); i++) multiFinger.move(event.getPointerId(i),
                        event.getX(i) / density, event.getY(i) / density, event.getEventTime());
                if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
                    MultiFingerGesture.Decision result = multiFinger.up(event.getPointerId(index), event.getX(index) / density,
                            event.getY(index) / density, event.getEventTime());
                    if (result != null && macGesturesEnabled) {
                        listener.gesture(result.gesture(), result.fingers());
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                }
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = startX = event.getX(); lastY = startY = event.getY();
                downAt = event.getEventTime(); lastScrollAt = 0; moved = twoFinger = scrolling = dragging = silenced = false;
                if (allowLongPress) postDelayed(longPress, 500); return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(longPress);
                if (allowLongPress && event.getPointerCount() == 2 && beforeTwoFinger != null) beforeTwoFinger.run();
                if (event.getPointerCount() >= 2) stopMomentum();
                if (dragging) { listener.pointer("leftUp", x, y); dragging = false; }
                if (event.getPointerCount() >= 3) return true;
                if (event.getPointerCount() == 2) {
                    twoFinger = true; lastX = startX = midX(event); lastY = startY = midY(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (silenced) return true;
                if (twoFinger && event.getPointerCount() >= 2) {
                    float cx = midX(event), cy = midY(event);
                    if (!scrolling && Math.hypot(cx - startX, cy - startY) > 8 * getResources().getDisplayMetrics().density) {
                        stopMomentum(); scrollSign = reversed ? -1 : 1; scrolling = true; listener.scroll("began", x, y, 0, 0);
                    }
                    if (scrolling && (cx != lastX || cy != lastY)) {
                        listener.scroll(null, x, y, scrollSign * (cx - lastX) / Math.max(1, areaWidth), scrollSign * (cy - lastY) / Math.max(1, areaWidth / 1.6f));
                        lastScrollAt = event.getEventTime();
                    }
                    lastX = cx; lastY = cy;
                } else if (!twoFinger) {
                    float cx = event.getX(), cy = event.getY();
                    if (!moved && Math.hypot(cx - startX, cy - startY) > 2 * getResources().getDisplayMetrics().density) {
                        moved = true; removeCallbacks(longPress);
                    }
                    if (moved || dragging) {
                        x = clamp(x + (cx - lastX) * 1.25f / Math.max(1, areaWidth));
                        y = clamp(y + (cy - lastY) * 1.25f / Math.max(1, areaWidth / 1.6f));
                        listener.pointer("move", x, y);
                    }
                    lastX = cx; lastY = cy;
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (silenced) return true;
                if (event.getPointerCount() > 2) {
                    if (event.getActionIndex() >= 2) return true;
                    if (scrolling) listener.scroll("ended", x, y, 0, 0);
                    scrolling = false; moved = true; twoFinger = true;
                    int first = event.getActionIndex() == 0 ? 1 : 0, second = 2;
                    lastX = startX = (event.getX(first) + event.getX(second)) / 2;
                    lastY = startY = (event.getY(first) + event.getY(second)) / 2;
                    return true;
                }
                if (scrolling) {
                    listener.scroll("ended", x, y, 0, 0); scrolling = false; moved = true;
                    startMomentum(event, areaWidth);
                    if (event.getPointerCount() == 2) {
                        int remaining = event.getActionIndex() == 0 ? 1 : 0;
                        lastX = event.getX(remaining); lastY = event.getY(remaining); twoFinger = false;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (dragging) listener.pointer("leftUp", x, y);
                else if (!silenced && !moved && event.getEventTime() - downAt < 500) {
                    String side = twoFinger ? "right" : "left";
                    listener.pointer(side + "Down", x, y); listener.pointer(side + "Up", x, y);
                    performClick();
                }
                touchActive = false; recycleVelocity(); return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGestures(); return true;
            default: return true;
        }
    }
    @Override protected void onDetachedFromWindow() { cancelGestures(); super.onDetachedFromWindow(); }
    @Override public boolean performClick() { super.performClick(); return true; }
}
