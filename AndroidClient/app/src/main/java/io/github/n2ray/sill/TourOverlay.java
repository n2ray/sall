package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** A session-local tour presentation. The owner supplies copy, anchors and lifecycle policy. */
final class TourOverlay extends FrameLayout {
    interface Actions { void next(); void skip(); }
    private static final int ACCENT = 0xff8eb9fb, CARD = 0xff24252a;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path holes = new Path();
    private final List<RectF> anchors = new ArrayList<>();
    private final LinearLayout card, content, footer;
    private final TextView heading, subtitle, progress;
    private final BoundedScroll scroll;
    private final Button next, skip;
    private boolean last, stream, stacked, coversTargets;
    private RectF pictureRoom;
    private float tailX;
    private int tailDirection;
    private final Actions actions;
    private TourPolicy.Topic focusedTopic;
    private TourContent.Copy shownCopy;

    TourOverlay(Context context, Actions actions) {
        super(context); this.actions = actions;
        setWillNotDraw(false); setClickable(true); setFocusableInTouchMode(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        card = new LinearLayout(context); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable background = new GradientDrawable(); background.setColor(CARD);
        background.setCornerRadius(dp(20)); background.setStroke(dp(1), 0x1affffff);
        card.setBackground(background); card.setElevation(dp(8));
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scroll = new BoundedScroll(context); scroll.setFillViewport(false);
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        heading = label(context, 17, Color.WHITE); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityHeading(true); content.addView(heading);
        subtitle = label(context, 13, 0xffb8bbc4); content.addView(subtitle);
        scroll.addView(content); card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        footer = new LinearLayout(context); footer.setGravity(Gravity.CENTER_VERTICAL);
        skip = new Button(context); skip.setAllCaps(false); skip.setText("跳过"); skip.setTextColor(ACCENT);
        skip.setOnClickListener(v -> actions.skip()); footer.addView(skip);
        progress = label(context, 14, ACCENT); progress.setGravity(Gravity.CENTER);
        footer.addView(progress);
        next = new Button(context); next.setAllCaps(false); next.setTextColor(Color.BLACK);
        GradientDrawable primary = new GradientDrawable(); primary.setColor(ACCENT); primary.setCornerRadius(dp(12));
        next.setBackground(primary);
        next.setOnClickListener(v -> actions.next()); footer.addView(next);
        card.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Rectangles are in this overlay's coordinates; callers recalculate after layout changes. */
    void showStep(TourPolicy.Run run, TourContent.Copy copy, List<RectF> targets, RectF room) {
        if (!copy.equals(shownCopy)) {
            heading.setText(copy.title());
            subtitle.setText(copy.subtitle()); subtitle.setVisibility(copy.subtitle() == null ? GONE : VISIBLE);
            while (content.getChildCount() > 2) content.removeViewAt(2);
            for (TourContent.Row row : copy.rows()) addRow(row);
            shownCopy = copy;
        }
        heading.setContentDescription(copy.title() + "，第 " + (run.index() + 1) + " 步，共 " + run.steps().size() + " 步。" + copy.hint());
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < run.steps().size(); i++) dots.append(i == run.index() ? "●" : "•").append(' ');
        progress.setText(dots.toString()); progress.setContentDescription("第 " + (run.index() + 1) + " 步，共 " + run.steps().size() + " 步");
        last = run.isLast(); next.setText(last ? "完成" : "下一步");
        skip.setVisibility(last ? INVISIBLE : VISIBLE); skip.setEnabled(!last);
        stream = run.at() == TourPolicy.Topic.TOUCH;
        pictureRoom = room == null ? null : new RectF(room);
        anchors.clear(); for (RectF target : targets) if (target != null && !target.isEmpty()) anchors.add(new RectF(target));
        requestLayout(); invalidate();
        if (focusedTopic != run.at()) {
            scroll.scrollTo(0, 0); focusedTopic = run.at();
            heading.post(() -> heading.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        }
    }
    private void addRow(TourContent.Row row) {
        LinearLayout line = new LinearLayout(getContext()); line.setGravity(Gravity.TOP);
        boolean largeText = getResources().getConfiguration().fontScale >= 1.5f;
        line.setOrientation(largeText ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        line.setPadding(0, dp(10), 0, 0);
        TextView icon = label(getContext(), 15, ACCENT); icon.setGravity(Gravity.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setText(row.symbol()); line.addView(icon, new LinearLayout.LayoutParams(largeText ? -2 : dp(25), -2));
        String prefix = row.added() ? "新增：" : "";
        SpannableString text = new SpannableString(prefix + row.emphasis() + row.text());
        if (!prefix.isEmpty()) {
            text.setSpan(new ForegroundColorSpan(ACCENT), 0, prefix.length(), 0);
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(), 0);
        }
        text.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), prefix.length() + row.emphasis().length(), 0);
        TextView words = label(getContext(), 15, 0xffe6e7eb); words.setText(text);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(largeText ? -1 : 0, -2, largeText ? 0 : 1);
        if (!largeText) p.leftMargin = dp(8);
        line.addView(words, p); content.addView(line);
    }
    boolean handleBack(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) { if (last) actions.next(); else actions.skip(); }
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return handleBack(event.getKeyCode(), event) || super.dispatchKeyEvent(event); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width, height);
        float density = getResources().getDisplayMetrics().density;
        boolean landscape = width > height;
        int cardWidth = Math.round(TourPlacement.width(width, height, landscape,
                getResources().getConfiguration().fontScale >= 1.5f, density));
        int innerWidth = Math.max(0, cardWidth - card.getPaddingLeft() - card.getPaddingRight());
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        skip.measure(free, free); next.measure(free, free); progress.measure(free, free);
        boolean nextStacked = skip.getMeasuredWidth() + next.getMeasuredWidth() + progress.getMeasuredWidth() > innerWidth;
        if (stacked != nextStacked || footer.getChildAt(0) != (nextStacked ? next : skip)) {
            stacked = nextStacked; footer.removeAllViews();
            if (stacked) { footer.addView(next); footer.addView(skip); footer.addView(progress); }
            else { footer.addView(skip); footer.addView(progress); footer.addView(next); }
        }
        footer.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        footer.setGravity(stacked ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        skip.setVisibility(last ? (stacked ? GONE : INVISIBLE) : VISIBLE);
        progress.setVisibility(stacked ? GONE : VISIBLE);
        footerParams(skip, stacked ? -1 : -2, 0);
        footerParams(progress, stacked ? -1 : 0, stacked ? 0 : 1);
        footerParams(next, stacked ? -1 : -2, 0);
        footer.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int available = Math.max(0, height - dp(32));
        scroll.maxHeight = Math.max(0, available - footer.getMeasuredHeight() - card.getPaddingTop() - card.getPaddingBottom());
        card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST));
        TourPlacement.Result placed = placement(width, height, cardWidth, card.getMeasuredHeight());
        int fit = Math.max(0, Math.round(placed.card().height()));
        if (fit < card.getMeasuredHeight()) {
            scroll.maxHeight = Math.max(0, fit - footer.getMeasuredHeight() - card.getPaddingTop() - card.getPaddingBottom());
            card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(fit, MeasureSpec.AT_MOST));
        }
    }
    private void footerParams(View view, int width, float weight) {
        LinearLayout.LayoutParams old = (LinearLayout.LayoutParams) view.getLayoutParams();
        if (old.width != width || old.weight != weight || old.height != LayoutParams.WRAP_CONTENT)
            view.setLayoutParams(new LinearLayout.LayoutParams(width, LayoutParams.WRAP_CONTENT, weight));
    }
    private TourPlacement.Rect rect(RectF r) {
        return r == null ? null : new TourPlacement.Rect(r.left, r.top, r.right, r.bottom);
    }
    private TourPlacement.Result placement(int w, int h, int cw, int ch) {
        RectF room = pictureRoom == null ? new RectF(0, 0, w, h) : pictureRoom;
        return TourPlacement.place(w, h, cw, ch, w > h, stream, rect(room), rect(room), rect(bounds()), getResources().getDisplayMetrics().density);
    }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        TourPlacement.Result p = placement(getWidth(), getHeight(), card.getMeasuredWidth(), card.getMeasuredHeight());
        tailDirection = p.tail(); tailX = p.tipX(); coversTargets = p.coversTargets();
        int x = Math.round(p.card().left()), y = Math.round(p.card().top());
        card.layout(x, y, x + card.getMeasuredWidth(), y + card.getMeasuredHeight());
    }
    private RectF bounds() {
        if (anchors.isEmpty()) return null;
        RectF union = new RectF(anchors.get(0));
        for (int i = 1; i < anchors.size(); i++) union.union(anchors.get(i));
        return union;
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); holes.reset();
        TourPlacement.Rect lit = coversTargets ? null : TourPlacement.cutout(rect(bounds()), getWidth(), getHeight(), getResources().getDisplayMetrics().density);
        RectF target = lit == null ? null : new RectF(lit.left(), lit.top(), lit.right(), lit.bottom());
        float radius = dp(stream ? 16 : 20);
        if (target != null) holes.addRoundRect(target, radius, radius, Path.Direction.CW);
        int saved = canvas.save(); canvas.clipOutPath(holes); canvas.drawColor(0x94000000); canvas.restoreToCount(saved);
        if (target != null) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(ACCENT); canvas.drawRoundRect(target, radius, radius, paint); }
        if (tailDirection != 0) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(CARD);
            float edge = tailDirection < 0 ? card.getTop() : card.getBottom();
            Path tail = new Path(); tail.moveTo(tailX - dp(8), edge); tail.lineTo(tailX, edge + tailDirection * dp(7)); tail.lineTo(tailX + dp(8), edge); tail.close(); canvas.drawPath(tail, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private TextView label(Context context, int size, int color) { TextView text = new TextView(context); text.setTextSize(size); text.setTextColor(color); return text; }
    private static final class BoundedScroll extends ScrollView {
        int maxHeight;
        BoundedScroll(Context context) { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int limit = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(MeasureSpec.getSize(heightSpec), maxHeight);
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        }
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
