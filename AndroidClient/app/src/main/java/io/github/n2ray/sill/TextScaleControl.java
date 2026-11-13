package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
        void rulerChanged(boolean open);
    }
    private final Delegate delegate;
    private PopupWindow popup;
    private Ruler ruler;
    private float downX;
    private double start, live;
    private boolean off, tracking, rulerVisible;
    private float stepPixels;
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
                boolean landscape = getRootView().getWidth() > getRootView().getHeight();
                float step = landscape ? (getHeight() / density >= 60 ? 44 : 40)
                        : Math.max(1, Math.min(36, ((getLeft() + getWidth() / 2f) / density * 2 - 60) / 4));
                stepPixels = step * density;
                int rulerWidth = Math.round((4 * step + 60) * density);
                ruler = new Ruler(getContext());
                int rulerHeight = ruler.heightFor(getHeight());
                popup = new PopupWindow(ruler, rulerWidth, rulerHeight, false);
                popup.setTouchable(false); popup.setClippingEnabled(true);
                popup.setOnDismissListener(() -> cancel("popup dismissed"));
                int[] location = new int[2]; getLocationOnScreen(location);
                try {
                    popup.showAtLocation(this, Gravity.TOP | Gravity.LEFT,
                            location[0] + getWidth() / 2 - rulerWidth / 2,
                            location[1] + (getHeight() - rulerHeight) / 2);
                    if (!tracking || popup == null) return false;
                    rulerVisible = true; delegate.rulerChanged(true);
                } catch (android.view.WindowManager.BadTokenException e) {
                    cancel("window unavailable"); return false;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                if (!delegate.canCommit()) { cancel("state changed"); return true; }
                double next = TextScale.snap(start + (event.getRawX() - downX) / stepPixels * .25, off);
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
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        PopupWindow shown = popup; popup = null; ruler = null;
        if (shown != null) shown.dismiss();
        if (rulerVisible) { rulerVisible = false; delegate.rulerChanged(false); }
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setEnabled(delegate.available());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                delegate.offersOff() ? .25f : .5f, 1.5f, (float) TextScale.position(delegate.scale(), delegate.offersOff())));
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
        private float middleY, titleTop, labelsTop;
        Ruler(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        int heightFor(int buttonHeight) {
            p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
            p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));
            float titleHeight = p.descent() - p.ascent();
            p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
            float labelsHeight = p.descent() - p.ascent();
            middleY = buttonHeight / 2f + 4 * density;
            titleTop = Math.max(middleY - 34 * density, -density);
            labelsTop = middleY + 9 * density;
            if (titleTop + titleHeight > middleY - 12 * density || labelsTop + labelsHeight > buttonHeight) {
                titleTop = 2 * density;
                middleY = titleTop + titleHeight + 14 * density;
                labelsTop = middleY + 13 * density;
                return Math.max(buttonHeight, (int) Math.ceil(labelsTop + labelsHeight + 2 * density));
            }
            return buttonHeight;
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float radius = (TextScaleControl.this.getHeight() / density >= 58 ? 16 : 14) * density;
            Path outline = new Path();
            outline.addRoundRect(new RectF(0, 0, getWidth(), getHeight()), radius, radius, Path.Direction.CW);
            int saved = c.save(); c.clipPath(outline);
            p.setStyle(Paint.Style.FILL); p.setColor(0xff2c3550);
            c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, p);
            float center = getWidth() / 2f, y = middleY;
            double first = off ? .25 : .5;
            float firstX = center + (float) ((first - live) / .25) * stepPixels;
            float lastX = center + (float) ((1.5 - live) / .25) * stepPixels;
            p.setStrokeWidth(3 * density); p.setStrokeCap(Paint.Cap.ROUND);
            p.setShader(new LinearGradient(0, 0, getWidth(), 0,
                    new int[]{0x00c4c9d1, 0x59c4c9d1, 0x59c4c9d1, 0x00c4c9d1},
                    new float[]{0, .12f, .88f, 1}, Shader.TileMode.CLAMP));
            c.drawLine(firstX, y, lastX, y, p); p.setShader(null);
            p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 10, getResources().getDisplayMetrics()));
            boolean sparseLabels = p.measureText("1.25") > stepPixels - 2 * density;
            for (double value = off ? .25 : .5; value <= 1.5; value += .25) {
                float x = center + (float)((value-live)/.25) * stepPixels;
                if (x < 4*density || x > getWidth()-4*density) continue;
                float fade = Math.min(1, Math.min(x, getWidth() - x) / (getWidth() * .12f));
                p.setColor(0xffc4c9d1); p.setAlpha(Math.round(153 * fade)); p.setStrokeWidth(2*density);
                p.setStrokeCap(Paint.Cap.BUTT); c.drawLine(x, y-4.5f*density, x, y+4.5f*density, p);
                p.setColor(value == live ? 0xffeef0f3 : 0xffc4c9d1); p.setAlpha(Math.round(255 * fade));
                p.setTextAlign(Paint.Align.CENTER);
                String title = TextScale.title(value);
                String label = value == .25 ? title : title.substring(0, title.length() - 1);
                if (!sparseLabels || value == live)
                    c.drawText(label, x, labelsTop - p.ascent(), p);
            }
            p.setAlpha(255); p.setColor(0xff9bb7ff); p.setShadowLayer(3*density, 0, density, 0x59000000);
            c.drawCircle(center, y, 11*density, p); p.clearShadowLayer();
            p.setColor(0xffeef0f3); p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 13, getResources().getDisplayMetrics()));
            p.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
            c.drawText(TextScale.title(live), center, titleTop - p.ascent(), p);
            c.restoreToCount(saved);
        }
    }
}
