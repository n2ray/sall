package io.github.n2ray.sill;

import java.text.BreakIterator;
import java.util.Locale;

/** The suffix typed since the last action that may have moved the Mac's caret. */
final class RemoteTextState {
    record Edit(int backspaces, String text) { }
    private String sent = "";
    private int fence;

    Edit reconcile(String next) { return reconcile(next, false); }

    Edit reconcile(String next, boolean backspaceFinal) {
        BreakIterator previous = BreakIterator.getCharacterInstance(Locale.ROOT);
        previous.setText(sent);
        int last = previous.preceding(sent.length());
        boolean deleteFinal = backspaceFinal && last != BreakIterator.DONE
                && next.equals(sent.substring(0, last));
        if (!deleteFinal && (next.length() < fence || !next.regionMatches(0, sent, 0, fence))) return null;
        int common = 0, limit = Math.min(sent.length(), next.length());
        while (common < limit && sent.charAt(common) == next.charAt(common)) common++;
        BreakIterator current = BreakIterator.getCharacterInstance(Locale.ROOT);
        current.setText(next);
        int minimum = deleteFinal ? 0 : fence;
        while (common > minimum && (!previous.isBoundary(common) || !current.isBoundary(common))) common--;
        int count = 0;
        if (common < sent.length())
            for (int at = previous.following(common); at != BreakIterator.DONE; at = previous.next()) count++;
        Edit edit = new Edit(count, next.substring(common));
        sent = next;
        fence = Math.min(fence, sent.length());
        for (int i = common; i < sent.length(); i++) {
            char c = sent.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') fence = i + 1;
        }
        return edit;
    }

    String text() { return sent; }
    int fence() { return fence; }
    static boolean splitsSurrogate(CharSequence text, int at) {
        return at > 0 && at < text.length() && Character.isHighSurrogate(text.charAt(at - 1))
                && Character.isLowSurrogate(text.charAt(at));
    }
    static int deletionStart(CharSequence text, int at) { return splitsSurrogate(text, at) ? at - 1 : at; }
    static int deletionEnd(CharSequence text, int at) { return splitsSurrogate(text, at) ? at + 1 : at; }
    void clear() { sent = ""; fence = 0; }
}
