package io.github.n2ray.sall;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

/** iOS-style direct picture input: tap, long press, scroll, pinch and Mac gestures. */
final class PictureInput implements View.OnTouchListener, View.OnGenericMotionListener {
    interface Listener {
        boolean beginPicture();
        void endPicture();
        boolean connected();
        boolean trackpadBusy();
        boolean reverseScrolling();
        FrameGeometry.Rect frame();
        void pointer(String action, float x, float y);
        void scroll(String phase, float x, float y, float dx, float dy);
        void gesture(String name, int fingers);
        void zoomChanged();
    }
    private final View view;
    private final PictureZoom zoom;
    private final Listener listener;
    private final PictureStroke stroke = new PictureStroke();
    private final MultiFingerGesture multi = new MultiFingerGesture();
    private final ScrollMomentum momentum = new ScrollMomentum();
    private final PhysicalMouse mouse;
    private final float density, slop;
    private boolean owned, scrollOpen, coasting, longDone, suppressTap, mouseOwned;
    private int a = -1, b = -1, sign = 1;
    private float startX, startY, lastX, lastY, pairX, pairY, pairSpan, zoomX, zoomY, zoomSpan;
    private float scrollX, scrollY, coastX, coastY;
    private long coastAt;
    private VelocityTracker velocity;
    private PictureZoom before;
    private final Runnable longPress;

    PictureInput(View view, PictureZoom zoom, Listener listener) {
        this.view = view; this.zoom = zoom; this.listener = listener;
        mouse = new PhysicalMouse(listener::pointer);
        density = view.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        longPress = () -> {
            if (!owned || !stroke.mayClick() || scrollOpen) return;
            FrameGeometry.Rect r = this.listener.frame();
            if (!inside(r, startX, startY)) return;
            float x = r.x(startX), y = r.y(startY);
            this.listener.pointer("move", x, y); this.listener.pointer("rightDown", x, y); this.listener.pointer("rightUp", x, y);
            longDone = true;
        };
    }
    private final Runnable coast = new Runnable() {
        public void run() {
            if (!coasting || !listener.connected()) { stopCoast(); return; }
            long now = SystemClock.uptimeMillis(); double dt = (now - coastAt) / 1000d;
            if (dt <= 0) { view.postDelayed(this, 16); return; }
            coastAt = now;
            if (!momentum.advance(dt, density)) { stopCoast(); return; }
            FrameGeometry.Rect r = listener.frame();
            listener.scroll(null, coastX, coastY, (float) momentum.stepX(dt) / r.width(), (float) momentum.stepY(dt) / r.height());
            view.postDelayed(this, 16);
        }
    };
    private static boolean inside(FrameGeometry.Rect r, float x, float y) {
        return x >= r.left() && x <= r.left() + r.width() && y >= r.top() && y <= r.top() + r.height();
    }
    private void stopCoast() {
        view.removeCallbacks(coast); momentum.clear();
        if (coasting) { coasting = false; listener.scroll("momentumEnded", coastX, coastY, 0, 0); }
    }
    void stopMomentum() { stopCoast(); }
    private void endScroll() {
        if (scrollOpen) { scrollOpen = false; listener.scroll("ended", scrollX, scrollY, 0, 0); }
    }
    void cancel() {
        view.removeCallbacks(longPress); endScroll(); stopCoast(); multi.cancel();
        mouse.cancel(); if (mouseOwned) { mouseOwned = false; listener.endPicture(); }
        if (velocity != null) { velocity.recycle(); velocity = null; }
        if (owned) listener.endPicture(); owned = false; a = b = -1;
    }
    private boolean mouseEvent(MotionEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_MOUSE)) return false;
        if (!listener.connected()) { if (mouseOwned) cancel(); return true; }
        boolean blocked = owned || listener.trackpadBusy();
        mouse.setBlocked(blocked);
        if (blocked) { if (mouseOwned) { mouseOwned = false; listener.endPicture(); } return true; }
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { cancel(); return true; }
        FrameGeometry.Rect r = listener.frame();
        float px = e.getX(), py = e.getY();
        boolean inside = inside(r, px, py);
        int buttons = action == MotionEvent.ACTION_UP ? 0 : e.getButtonState();
        if (!mouseOwned && inside && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS)
                && (buttons & (MotionEvent.BUTTON_PRIMARY | MotionEvent.BUTTON_SECONDARY)) != 0) {
            if (!listener.beginPicture()) return true;
            stopCoast(); mouseOwned = true;
        }
        mouse.update(mouseOwned ? buttons : 0, r.x(px), r.y(py), inside);
        if (mouseOwned && !mouse.held()) { mouseOwned = false; listener.endPicture(); }
        if (action == MotionEvent.ACTION_SCROLL && inside) {
            stopCoast();
            ViewConfiguration config = ViewConfiguration.get(view.getContext());
            float dx = e.getAxisValue(MotionEvent.AXIS_HSCROLL) * config.getScaledHorizontalScrollFactor() / r.width();
            float dy = e.getAxisValue(MotionEvent.AXIS_VSCROLL) * config.getScaledVerticalScrollFactor() / r.height();
            if (Float.isFinite(dx) && Float.isFinite(dy) && (dx != 0 || dy != 0))
                listener.scroll(null, r.x(px), r.y(py), dx, dy);
        }
        return true;
    }
    @Override public boolean onGenericMotion(View v, MotionEvent e) { return mouseEvent(e); }
    private void startScroll(float x, float y) {
        stopCoast(); FrameGeometry.Rect r = listener.frame(); scrollX = r.x(x); scrollY = r.y(y);
        sign = listener.reverseScrolling() ? -1 : 1; scrollOpen = true; listener.scroll("began", scrollX, scrollY, 0, 0);
    }
    private void moveScroll(float x, float y) {
        FrameGeometry.Rect r = listener.frame(); scrollX = r.x(x); scrollY = r.y(y);
        float dx = (x - lastX) / r.width() * sign, dy = (y - lastY) / r.height() * sign;
        if (dx != 0 || dy != 0) listener.scroll(null, scrollX, scrollY, dx, dy);
        lastX = x; lastY = y;
    }
    private void startCoast(MotionEvent e) {
        if (velocity == null) return;
        velocity.computeCurrentVelocity(1000);
        float vx = velocity.getXVelocity(e.getPointerId(0)) * sign, vy = velocity.getYVelocity(e.getPointerId(0)) * sign;
        if (!momentum.start(vx, vy, density)) return;
        FrameGeometry.Rect r = listener.frame(); coastX = scrollX; coastY = scrollY; coasting = true;
        listener.scroll("momentumBegan", coastX, coastY, 0, 0);
        listener.scroll(null, coastX, coastY, (float) momentum.firstX() / r.width(), (float) momentum.firstY() / r.height());
        coastAt = SystemClock.uptimeMillis(); view.postDelayed(coast, 16);
    }
    public boolean onTouch(View v, MotionEvent e) {
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) return mouseEvent(e);
        int action = e.getActionMasked(), index = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            if (mouseOwned) return true;
            if (!listener.connected() || !listener.beginPicture()) return false;
            owned = true; stopCoast(); stroke.begin(); multi.cancel(); before = zoom.copy();
            longDone = suppressTap = false; a = e.getPointerId(0); b = -1;
            startX = lastX = e.getX(); startY = lastY = e.getY();
            velocity = VelocityTracker.obtain(); view.postDelayed(longPress, 450);
        }
        if (!owned) return false;
        if (!listener.connected()) { cancel(); return true; }
        if (velocity != null) velocity.addMovement(e);
        boolean silencedNow = false;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            for (int i = 0; i < e.getPointerCount(); i++) if (i != index)
                multi.move(e.getPointerId(i), e.getX(i) / density, e.getY(i) / density, e.getEventTime());
            silencedNow = multi.down(e.getPointerId(index), e.getX(index) / density, e.getY(index) / density, e.getEventTime(), longDone);
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            for (int i = 0; i < e.getPointerCount(); i++) multi.move(e.getPointerId(i), e.getX(i) / density, e.getY(i) / density, e.getEventTime());
            if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
                MultiFingerGesture.Decision result = multi.up(e.getPointerId(index), e.getX(index) / density, e.getY(index) / density, e.getEventTime());
                if (result != null && stroke.owner() == PictureStroke.Owner.GESTURE) listener.gesture(result.gesture(), result.fingers());
            }
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            view.removeCallbacks(longPress); suppressTap = true;
            if (e.getPointerCount() >= 3 && silencedNow) {
                if (stroke.owner() == PictureStroke.Owner.PICTURE) { zoom.restore(before); listener.zoomChanged(); }
                stroke.third(); endScroll(); stopCoast();
            } else if (e.getPointerCount() == 2) {
                b = e.getPointerId(index); int ai = e.findPointerIndex(a), bi = e.findPointerIndex(b);
                if (ai >= 0 && bi >= 0) {
                    pairX = zoomX = (e.getX(ai) + e.getX(bi)) / 2; pairY = zoomY = (e.getY(ai) + e.getY(bi)) / 2;
                    pairSpan = zoomSpan = (float) Math.hypot(e.getX(ai) - e.getX(bi), e.getY(ai) - e.getY(bi));
                    lastX = pairX; lastY = pairY; stroke.second(zoom.isZoomed());
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE && !longDone) {
            if (e.getPointerCount() >= 2 && b >= 0) {
                int ai = e.findPointerIndex(a), bi = e.findPointerIndex(b);
                if (ai >= 0 && bi >= 0) {
                    float x = (e.getX(ai) + e.getX(bi)) / 2, y = (e.getY(ai) + e.getY(bi)) / 2;
                    float span = (float) Math.hypot(e.getX(ai) - e.getX(bi), e.getY(ai) - e.getY(bi));
                    stroke.pairMove(Math.abs(span - pairSpan), (float) Math.hypot(x - pairX, y - pairY), slop, 12 * density);
                    if (stroke.owner() == PictureStroke.Owner.PICTURE && zoomSpan >= 1 && span >= 1) {
                        zoom.change(span / zoomSpan, zoomX, zoomY, x - zoomX, y - zoomY, v.getWidth(), v.getHeight(), listener.frame().width(), listener.frame().height());
                        zoomX = x; zoomY = y; zoomSpan = span; listener.zoomChanged();
                    } else if (stroke.owner() == PictureStroke.Owner.MAC) {
                        if (!scrollOpen) startScroll(x, y); moveScroll(x, y);
                    }
                }
            } else if (stroke.owner() != PictureStroke.Owner.PICTURE && stroke.owner() != PictureStroke.Owner.GESTURE) {
                float x = e.getX(), y = e.getY();
                stroke.singleMove((float) Math.hypot(x - startX, y - startY), slop);
                if (stroke.owner() == PictureStroke.Owner.MAC) {
                    view.removeCallbacks(longPress);
                    if (!scrollOpen) startScroll(x, y); moveScroll(x, y);
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP && e.getPointerCount() == 2) {
            int left = index == 0 ? 1 : 0; a = e.getPointerId(left); b = -1; lastX = e.getX(left); lastY = e.getY(left);
        } else if (action == MotionEvent.ACTION_UP) {
            view.removeCallbacks(longPress);
            if (scrollOpen) { endScroll(); startCoast(e); }
            else if (!longDone && !suppressTap && stroke.mayClick()) {
                FrameGeometry.Rect r = listener.frame();
                if (inside(r, e.getX(), e.getY())) {
                    float x = r.x(e.getX()), y = r.y(e.getY());
                    listener.pointer("move", x, y); listener.pointer("leftDown", x, y); listener.pointer("leftUp", x, y);
                }
            }
            if (stroke.owner() == PictureStroke.Owner.PICTURE) { zoom.settle(); listener.zoomChanged(); }
            if (velocity != null) { velocity.recycle(); velocity = null; }
            listener.endPicture(); owned = false;
        } else if (action == MotionEvent.ACTION_CANCEL) cancel();
        return true;
    }
}
