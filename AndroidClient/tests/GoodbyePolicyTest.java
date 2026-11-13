package io.github.n2ray.sall;

import java.util.Map;

public class GoodbyePolicyTest {
    public static void main(String[] args) {
        var quit = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "quit", "reconnect", false)), "Mac", true);
        assert quit.reconnect() && quit.remoteAllowed() && !quit.notice();
        var removed = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "removed", "reconnect", true)), "Mac", true);
        assert !removed.reconnect() && !removed.remoteAllowed() && !removed.notice();
        for (String reason : new String[]{"remoteOff", "internetOff"}) {
            var result = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", reason)), "Mac", true);
            assert result.reconnect() && !result.remoteAllowed();
        }
        assert GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "busy")), "Mac", true).remoteAllowed();
        assert !GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "pairingRequired")), "Mac", true).reconnect();
        var notice = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "future", "message", "\u202eWarning\nnow\u0007", "reconnect", true)), "Mac", true);
        assert notice.notice() && notice.reconnect() && notice.text().equals("Warning now");
        var malformed = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "quit", "reconnect", "true")), "Mac", true);
        assert malformed.notice() && !malformed.reconnect();
        assert GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of()), "Mac", true).notice();
        var update = GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "update", "minimumVersion", "v2.0.0")), "Mac", true);
        assert update.update() && !update.reconnect() && update.text().contains("2.0");
        assert !GoodbyePolicy.outcome(GoodbyePolicy.decode(Map.of("reason", "update", "minimumVersion", "1..2")), "Mac", true).text().contains("1..2");
        assert GoodbyePolicy.clean(" a\u200b\u202e \n b ", 300).equals("a b");
        assert GoodbyePolicy.clean("x".repeat(400), 300).length() == 300;
        assert GoodbyePolicy.clean("é".repeat(400), 300).length() == 300;
        assert GoodbyePolicy.clean("e\u0301".repeat(400), 300).equals("e\u0301".repeat(300));
        assert GoodbyePolicy.outcome(null, "Mac", true).reconnect();
        assert !new GoodbyePolicy.Ended(notice).getMessage().contains(notice.text());
        System.out.println("Goodbye policy checks passed");
    }
}
