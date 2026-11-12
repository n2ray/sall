package io.github.n2ray.sill;

import java.text.BreakIterator;
import java.util.Locale;
import java.util.Map;

/** Interpretation of host kind 22; JSON transport decoding remains at the trust boundary. */
final class GoodbyePolicy {
    record Goodbye(String reason, String message, String minimumVersion, Boolean reconnect) {}
    record Outcome(String text, boolean reconnect, boolean remoteAllowed, boolean update, boolean notice) {}

    /** A malformed kind 22 is an unknown notice, not an ordinary disconnect. */
    static Goodbye decode(Map<String, ?> fields) {
        if (fields == null || !(fields.get("reason") instanceof String reason)
                || !optional(fields.get("message"), String.class)
                || !optional(fields.get("minimumVersion"), String.class)
                || !optional(fields.get("reconnect"), Boolean.class)) return new Goodbye("", null, null, null);
        return new Goodbye(reason, (String) fields.get("message"), (String) fields.get("minimumVersion"),
                (Boolean) fields.get("reconnect"));
    }

    private static boolean optional(Object value, Class<?> type) { return value == null || type.isInstance(value); }

    static Outcome outcome(Goodbye goodbye, String mac, boolean saved) {
        String name = clean(mac, 64);
        if (name.isEmpty()) name = "Mac";
        if (goodbye == null) return new Outcome(name + "已断开连接，将在可以连接时重试。", true, saved, false, false);
        return switch (goodbye.reason()) {
            case "quit" -> new Outcome(name + "已退出 Sill，将在它重新启动后连接。", true, saved, false, false);
            case "removed" -> new Outcome(name + "已移除此设备，请重新配对。", false, false, false, false);
            case "remoteOff" -> new Outcome(name + "已关闭远程访问。", true, false, false, false);
            case "internetOff" -> new Outcome(name + "已停止接受互联网连接，请通过 VPN 连接。", true, false, false, false);
            case "busy" -> new Outcome(name + "已连接 8 台设备，请稍后再试。", true, saved, false, false);
            case "pairingRequired" -> new Outcome(name + "现在要求配对，请重新配对此设备。", false, false, false, false);
            default -> {
                boolean retry = Boolean.TRUE.equals(goodbye.reconnect());
                boolean update = "update".equals(goodbye.reason());
                String text = clean(goodbye.message(), 300);
                if (text.isEmpty()) {
                    if (update) {
                        text = "请更新此设备上的 Sill，以继续使用 " + name + "。";
                        String version = version(goodbye.minimumVersion());
                        if (version != null) text += "需要 " + version + " 或更高版本。";
                    } else text = name + (retry ? "已关闭连接，将在可以连接时重试。" : "已关闭连接，请点击重试。");
                }
                yield new Outcome(text, retry, saved, update, true);
            }
        };
    }

    /** No host message or raw JSON is included in logs via this exception. */
    static final class Ended extends Exception {
        final Outcome outcome;
        Ended(Outcome outcome) { super("Mac ended the session"); this.outcome = outcome; }
    }

    static String clean(String input, int limit) {
        if (input == null || limit <= 0) return "";
        StringBuilder text = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < input.length();) {
            int cp = input.codePointAt(i); i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { space = text.length() > 0; continue; }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE
                    || type == Character.PRIVATE_USE || type == Character.UNASSIGNED) continue;
            if (space) { text.append(' '); space = false; }
            text.appendCodePoint(cp);
        }
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text.toString());
        int end = characters.first();
        for (int n = 0; n < limit; n++) {
            int next = characters.next();
            if (next == BreakIterator.DONE) return text.toString();
            end = next;
        }
        while (end > 0 && text.charAt(end - 1) == ' ') end--;
        return text.substring(0, end);
    }

    private static String version(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int n = 0;
        while (n < s.length() && ((s.charAt(n) >= '0' && s.charAt(n) <= '9') || s.charAt(n) == '.')) n++;
        s = s.substring(0, n).replaceFirst("\\.+$", "");
        if (s.isEmpty()) return null;
        String[] parts = s.split("\\.", -1);
        if (parts.length > 8) return null;
        java.util.ArrayList<Integer> numbers = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 9) return null;
            try { numbers.add(Integer.parseInt(part)); } catch (NumberFormatException e) { return null; }
        }
        while (!numbers.isEmpty() && numbers.get(numbers.size() - 1) == 0) numbers.remove(numbers.size() - 1);
        while (numbers.size() < 2) numbers.add(0);
        return numbers.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("."));
    }
}
