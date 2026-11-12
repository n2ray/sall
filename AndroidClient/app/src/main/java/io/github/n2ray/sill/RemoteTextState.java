package io.github.n2ray.sill;

import java.text.BreakIterator;
import java.util.Locale;

/** The suffix typed since the last action that may have moved the Mac's caret. */
final class RemoteTextState {
    record Edit(int backspaces, String text) { }
    private String sent = "";

    Edit reconcile(String next) {
        int common = 0, limit = Math.min(sent.length(), next.length());
        while (common < limit && sent.charAt(common) == next.charAt(common)) common++;
        BreakIterator previous = BreakIterator.getCharacterInstance(Locale.ROOT);
        BreakIterator current = BreakIterator.getCharacterInstance(Locale.ROOT);
        previous.setText(sent); current.setText(next);
        while (common > 0 && (!previous.isBoundary(common) || !current.isBoundary(common))) common--;
        int count = 0;
        if (common < sent.length())
            for (int at = previous.following(common); at != BreakIterator.DONE; at = previous.next()) count++;
        Edit edit = new Edit(count, next.substring(common));
        sent = next;
        return edit;
    }

    void clear() { sent = ""; }
}
