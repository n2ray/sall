package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.PopupWindow;

/** Press and drag the ruler; a completed gesture sends at most one new viewport. */
final class TextScaleControl extends Button {
    interface Delegate {
        boolean available();
        boolean offersOff();
        Double scale();
        void begin();
        boolean canCommit();
        void commit(Double scale);
    }
    private final Delegate delegate;
    private PopupWindow popup;
    private Ruler ruler;
    private float downX;
    private double start, live;
    private boolean off, tracking;
    private final float density;

    TextScaleControl(Context context, Delegate delegate) {
        super(context); this.delegate = delegate; density = getResources().getDisplayMetrics().density;
        setAllCaps(false); refresh();
    }
    void refresh() {
        if (tracking && !delegate.canCommit()) cancel("state changed");
        setAlpha(delegate.available() ? 1f : .35f);
        String value = TextScale.title(TextScale.position(delegate.scale(), delegate.offersOff()));
        setText("Aa\n" + value); setContentDescription("文字大小，" + value + (delegate.available() ? "" : "，选择窗口后可用"));
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (tracking && !TextScale.singlePointer(event.getPointerCount())) { cancel("multiple pointers"); return true; }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!delegate.available()) return false;
                delegate.begin();
                if (!delegate.available()) return false;
                off = delegate.offersOff(); start = live = TextScale.position(delegate.scale(), off);
                downX = event.getRawX(); tracking = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                ruler = new Ruler(getContext());
                popup = new PopupWindow(ruler, Math.round(236 * density), Math.round(58 * density), false);
                popup.setTouchable(false); popup.setClippingEnabled(true);
                int[] location = new int[2]; getLocationOnScreen(location);
                popup.showAtLocation(this, Gravity.TOP | Gravity.LEFT,
                        location[0] + getWidth() / 2 - Math.round(118 * density), location[1]);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                if (!delegate.canCommit()) { cancel("state changed"); return true; }
                double next = TextScale.snap(start + (event.getRawX() - downX) / (44 * density) * .25, off);
                if (next != live) { live = next; ruler.invalidate(); performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); }
                return true;
            case MotionEvent.ACTION_UP:
                if (!tracking) return false;
                boolean apply = delegate.canCommit() && live != start;
                double chosen = live;
                android.util.Log.i("SillAndroid", "Aa: release apply=" + apply + " start=" + start + " chosen=" + chosen);
                cancel("released");
                if (apply) delegate.commit(TextScale.scale(chosen));
                refresh(); performClick(); return true;
            case MotionEvent.ACTION_CANCEL: cancel("touch cancelled"); return true;
            default: return tracking;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { cancel(); super.onDetachedFromWindow(); }
    void cancel() { cancel("layout or lifecycle"); }
    private void cancel(String reason) {
        if (tracking && !reason.equals("released")) android.util.Log.i("SillAndroid", "Aa: cancelled (" + reason + ")");
        tracking = false;
        if (popup != null) popup.dismiss();
        popup = null; ruler = null;
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (delegate.available()) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }
    @Override public boolean performAccessibilityAction(int action, android.os.Bundle arguments) {
        if ((action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) && delegate.available()) {
            delegate.begin();
            double pos = TextScale.position(delegate.scale(), delegate.offersOff());
            double next = TextScale.snap(pos + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? .25 : -.25), delegate.offersOff());
            if (delegate.canCommit() && next != pos) delegate.commit(TextScale.scale(next));
            refresh(); return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
    private final class Ruler extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ruler(Context context) { super(context); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            p.setColor(0xff30343c); c.drawRoundRect(0, 0, getWidth(), getHeight(), 12*density, 12*density, p);
            float center = getWidth()/2f, y = 22*density, spacing = 44*density;
            for (double value = off ? .25 : .5; value <= 1.5; value += .25) {
                float x = center + (float)((value-live)/.25) * spacing;
                if (x < 4*density || x > getWidth()-4*density) continue;
                p.setColor(value == live ? Color.WHITE : 0xffa7adb8); p.setStrokeWidth(2*density);
                c.drawLine(x, y-4*density, x, y+5*density, p);
                p.setTextSize(10*density); p.setTextAlign(Paint.Align.CENTER);
                c.drawText(TextScale.title(value), x, y+20*density, p);
            }
            p.setColor(Color.WHITE); c.drawCircle(center, y, 3*density, p);
        }
    }
}
