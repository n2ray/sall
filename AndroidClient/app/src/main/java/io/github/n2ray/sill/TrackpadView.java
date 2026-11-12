package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Relative pointer and two-finger scroll in the same normalized coordinates as Sill. */
final class TrackpadView extends View {
    interface Listener {
        void pointer(String action, float x, float y);
        void scroll(String phase, float x, float y, float dx, float dy);
    }
    private final Listener listener;
    private final Paint paint = new Paint(3);
    private float x = .5f, y = .5f, lastX, lastY, startX, startY;
    private long downAt;
    private boolean moved, twoFinger, scrolling, dragging;
    private final Runnable longPress;

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
    void position(float x, float y) { this.x = clamp(x); this.y = clamp(y); }
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
        return touch(event, getWidth());
    }
    boolean touch(MotionEvent event, float areaWidth) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = startX = event.getX(); lastY = startY = event.getY();
                downAt = event.getEventTime(); moved = twoFinger = scrolling = dragging = false;
                postDelayed(longPress, 500); return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                removeCallbacks(longPress);
                if (dragging) { listener.pointer("leftUp", x, y); dragging = false; }
                if (event.getPointerCount() == 2) {
                    twoFinger = true; lastX = startX = midX(event); lastY = startY = midY(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (twoFinger && event.getPointerCount() >= 2) {
                    float cx = midX(event), cy = midY(event);
                    if (!scrolling && Math.hypot(cx - startX, cy - startY) > 8 * getResources().getDisplayMetrics().density) {
                        scrolling = true; listener.scroll("began", x, y, 0, 0);
                    }
                    if (scrolling) listener.scroll(null, x, y, (cx - lastX) / Math.max(1, areaWidth), (cy - lastY) / Math.max(1, areaWidth / 1.6f));
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
                if (scrolling) { listener.scroll("ended", x, y, 0, 0); scrolling = false; moved = true; }
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (dragging) listener.pointer("leftUp", x, y);
                else if (!moved && event.getEventTime() - downAt < 500) {
                    String side = twoFinger ? "right" : "left";
                    listener.pointer(side + "Down", x, y); listener.pointer(side + "Up", x, y);
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (scrolling) listener.scroll("ended", x, y, 0, 0);
                if (dragging) listener.pointer("leftUp", x, y);
                scrolling = dragging = false; return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
