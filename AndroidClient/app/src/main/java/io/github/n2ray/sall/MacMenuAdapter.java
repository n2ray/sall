package io.github.n2ray.sall;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Menu rows keep mark, subtitle and submenu affordance separate from their accessible label. */
final class MacMenuAdapter extends BaseAdapter {
    private final Context context;
    private final List<MacMenuState.Row> rows;
    private final boolean stale;
    MacMenuAdapter(Context context, List<MacMenuState.Row> rows, boolean stale) {
        this.context = context; this.rows = Collections.unmodifiableList(new ArrayList<>(rows)); this.stale = stale;
    }
    private int dp(float n) { return Math.round(n * context.getResources().getDisplayMetrics().density); }
    @Override public int getCount() { return rows.size(); }
    @Override public MacMenuState.Row getItem(int position) { return rows.get(position); }
    @Override public long getItemId(int position) { return position; }
    @Override public boolean areAllItemsEnabled() { return false; }
    @Override public boolean isEnabled(int position) {
        MacMenuState.Row row = getItem(position);
        return row != null && row.kind() != MacMenuState.Kind.NOTE && row.enabled() && !stale && row.id() != null;
    }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int position) { return getItem(position) == null ? 0 : 1; }
    private static final class Holder {
        final TextView mark, title, subtitle, arrow;
        Holder(TextView mark, TextView title, TextView subtitle, TextView arrow) {
            this.mark = mark; this.title = title; this.subtitle = subtitle; this.arrow = arrow;
        }
    }
    private TextView text(float size, int color) {
        TextView view = new TextView(context); view.setTextSize(size); view.setTextColor(color);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return view;
    }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        MacMenuState.Row row = getItem(position);
        if (row == null) {
            if (convertView == null) {
                LinearLayout separator = new LinearLayout(context); separator.setOrientation(LinearLayout.VERTICAL);
                separator.setGravity(Gravity.CENTER_VERTICAL); separator.setPadding(dp(16), 0, dp(16), 0);
                separator.setMinimumHeight(dp(9)); separator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                View line = new View(context); line.setBackgroundColor(0x26ffffff);
                separator.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)))); convertView = separator;
            }
            return convertView;
        }
        Holder holder;
        if (convertView == null) {
            LinearLayout line = new LinearLayout(context); line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL); line.setPadding(dp(16), dp(8), dp(16), dp(8));
            line.setMinimumHeight(dp(44)); line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            TextView mark = text(17, 0xffeef0f3); mark.setGravity(Gravity.CENTER);
            line.addView(mark, new LinearLayout.LayoutParams(dp(22), -2));
            LinearLayout words = new LinearLayout(context); words.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, -2, 1); wordsParams.leftMargin = dp(8); line.addView(words, wordsParams);
            TextView title = text(17, 0xffeef0f3); title.setMaxLines(3); title.setEllipsize(TextUtils.TruncateAt.END); words.addView(title);
            TextView subtitle = text(13, 0xffa4abb5); subtitle.setMaxLines(3); subtitle.setEllipsize(TextUtils.TruncateAt.END); words.addView(subtitle);
            TextView arrow = text(22, 0xffa4abb5); arrow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(24), -2); arrowParams.leftMargin = dp(4); line.addView(arrow, arrowParams);
            holder = new Holder(mark, title, subtitle, arrow); line.setTag(holder); convertView = line;
        } else holder = (Holder) convertView.getTag();
        boolean note = row.kind() == MacMenuState.Kind.NOTE, enabled = isEnabled(position);
        MacMenuRow.NoteText noteText = note ? MacMenuRow.noteText(row.title()) : null;
        String title = note ? noteText.title() : row.title();
        String subtitle = note ? noteText.subtitle() : row.key();
        LinearLayout words = (LinearLayout) holder.title.getParent();
        LinearLayout.LayoutParams wordsParams = (LinearLayout.LayoutParams) words.getLayoutParams();
        int margin = note ? 0 : dp(8);
        if (wordsParams.leftMargin != margin) { wordsParams.leftMargin = margin; words.setLayoutParams(wordsParams); }
        holder.mark.setVisibility(note ? View.GONE : View.VISIBLE);
        holder.mark.setText(row.kind() != MacMenuState.Kind.ITEM ? ""
                : row.mark() == MacMenuState.Mark.ON ? "✓" : row.mark() == MacMenuState.Mark.MIXED ? "−" : "");
        holder.title.setText(title); holder.subtitle.setText(subtitle);
        holder.subtitle.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        holder.arrow.setText("›"); holder.arrow.setVisibility(!note && enabled && row.kind() == MacMenuState.Kind.SUBMENU ? View.VISIBLE : View.GONE);
        convertView.setEnabled(enabled); convertView.setAlpha(enabled ? 1f : .45f);
        StringBuilder spoken = new StringBuilder(title);
        if (row.kind() == MacMenuState.Kind.ITEM && row.mark() == MacMenuState.Mark.ON) spoken.append("，已选中");
        else if (row.kind() == MacMenuState.Kind.ITEM && row.mark() == MacMenuState.Mark.MIXED) spoken.append("，部分选中");
        if (subtitle != null && !subtitle.isEmpty()) spoken.append("，").append(subtitle);
        if (row.kind() == MacMenuState.Kind.SUBMENU && enabled) spoken.append("，子菜单");
        convertView.setContentDescription(spoken.toString());
        return convertView;
    }
}
