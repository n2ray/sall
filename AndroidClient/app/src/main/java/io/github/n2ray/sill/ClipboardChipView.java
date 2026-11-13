package io.github.n2ray.sill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A wrap-content overlay; only its own bounds participate in touch dispatch. */
final class ClipboardChipView extends LinearLayout {
    interface Actions { void paste(); void dismiss(); }
    private final TextView icon, title, subtitle, note;
    private final LinearLayout words;
    private final Button paste, dismiss;

    ClipboardChipView(Context context, Actions actions) {
        super(context); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14), dp(6), dp(6), dp(6));
        GradientDrawable capsule = new GradientDrawable(); capsule.setColor(0xff16181c);
        capsule.setCornerRadius(dp(100)); capsule.setStroke(dp(1), 0x1affffff); setBackground(capsule);
        setElevation(dp(16));
        icon = new TextView(context); icon.setText("▣"); icon.setTextSize(15); icon.setTextColor(0xffa4abb5);
        icon.setGravity(Gravity.CENTER); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setAutoSizeTextTypeUniformWithConfiguration(8, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        addView(icon, new LinearLayout.LayoutParams(dp(18), dp(32)));
        words = new LinearLayout(context); words.setOrientation(VERTICAL);
        LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, -2, 1);
        wordsParams.leftMargin = dp(10); wordsParams.rightMargin = dp(10); addView(words, wordsParams);
        title = new TextView(context); title.setTextSize(13); title.setTextColor(0xffeef0f3);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.MIDDLE); words.addView(title);
        subtitle = new TextView(context); subtitle.setTextSize(12); subtitle.setTextColor(0xffa4abb5);
        subtitle.setSingleLine(true); subtitle.setEllipsize(TextUtils.TruncateAt.END); words.addView(subtitle);
        words.setFocusable(true);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        note = new TextView(context); note.setTextSize(13); note.setTextColor(0xffeef0f3);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(0, -2, 1);
        noteParams.leftMargin = dp(8); noteParams.rightMargin = dp(8); addView(note, noteParams);
        paste = new Button(context); paste.setAllCaps(false); paste.setText("粘贴"); paste.setTextSize(13);
        paste.setTextColor(Color.BLACK); paste.setPadding(dp(12), dp(4), dp(12), dp(4)); paste.setMinWidth(0);
        paste.setMinimumWidth(0); paste.setMinHeight(dp(32)); paste.setMinimumHeight(dp(32));
        GradientDrawable pasteShape = new GradientDrawable(); pasteShape.setColor(0xff9bb7ff);
        pasteShape.setCornerRadius(dp(40)); paste.setBackground(pasteShape);
        paste.setOnClickListener(v -> actions.paste()); addView(paste, new LinearLayout.LayoutParams(-2, -2));
        dismiss = new Button(context); dismiss.setAllCaps(false); dismiss.setText("×"); dismiss.setTextSize(18);
        dismiss.setTextColor(0xffa4abb5); dismiss.setContentDescription("关闭剪贴板提示");
        dismiss.setPadding(0, 0, 0, 0); dismiss.setMinWidth(0); dismiss.setMinimumWidth(0);
        dismiss.setMinHeight(0); dismiss.setMinimumHeight(0); dismiss.setBackgroundColor(Color.TRANSPARENT);
        dismiss.setAutoSizeTextTypeUniformWithConfiguration(8, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        dismiss.setOnClickListener(v -> actions.dismiss()); addView(dismiss, new LinearLayout.LayoutParams(dp(32), dp(32)));
        setVisibility(GONE);
    }
    void offer(String macName, boolean pasteRightAway, boolean pasteEnabled) {
        String mac = macName == null || macName.trim().isEmpty() ? "Mac" : macName;
        icon.setText("▣"); icon.setTextColor(0xffa4abb5);
        title.setText("粘贴到 " + mac);
        subtitle.setText(pasteRightAway ? "粘贴到光标所在位置" : "随后在 Mac 上按 ⌘V");
        words.setContentDescription("粘贴到 " + mac + "。" + subtitle.getText());
        paste.setContentDescription("粘贴到 " + mac); paste.setEnabled(pasteEnabled);
        paste.setAlpha(pasteEnabled ? 1f : .35f);
        words.setVisibility(VISIBLE); paste.setVisibility(VISIBLE); dismiss.setVisibility(VISIBLE);
        note.setVisibility(GONE); setClickable(true); setVisibility(VISIBLE);
    }
    void note(ClipboardChipState.Note message, String macName) {
        String mac = macName == null || macName.trim().isEmpty() ? "Mac" : macName;
        boolean warning = message == ClipboardChipState.Note.FAILED || message == ClipboardChipState.Note.TOO_LONG;
        icon.setText(warning ? "⚠" : "✓"); icon.setTextColor(warning ? 0xffff9f0a : 0xff9bb7ff);
        note.setText(switch (message) {
            case SENT -> "已发送到 " + mac + " 的剪贴板";
            case PASTE_REQUESTED -> "已向 " + mac + " 发送粘贴";
            case FAILED -> "未能发送到 " + mac;
            case TOO_LONG -> "文字过长，无法发送到 " + mac;
        });
        words.setVisibility(GONE); paste.setVisibility(GONE); dismiss.setVisibility(GONE);
        note.setVisibility(VISIBLE); setClickable(false); setVisibility(VISIBLE);
    }
    void hide() { setVisibility(GONE); }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
