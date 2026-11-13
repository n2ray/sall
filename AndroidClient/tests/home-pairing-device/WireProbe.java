package io.github.n2ray.sall;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Runs synthetic bytes on Android's own JSON and stream implementation; no app data or network. */
public final class WireProbe {
    private static ByteArrayInputStream response(String json) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamWire.write(out, 20, json.getBytes(StandardCharsets.UTF_8));
        return new ByteArrayInputStream(out.toByteArray());
    }
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        var shown = SillConnection.homePairingCommand(response("{\"ok\":false,\"reason\":\"shown\"}"), out, "ask", "Synthetic", "Test");
        if (!shown.reason().equals("shown")) throw new AssertionError("shown");
        StreamWire.Message frame = StreamWire.read(new ByteArrayInputStream(out.toByteArray()));
        JSONObject request = new JSONObject(new String(frame.payload(), StandardCharsets.UTF_8));
        if (frame.kind() != 19 || request.getInt("v") != 1 || !request.getString("method").equals("ask") || !request.getString("proof").isEmpty()) throw new AssertionError("request");
        out.reset();
        var closed = SillConnection.homePairingCommand(response("{\"ok\":false,\"reason\":\"closed\"}"), out, "cancel", "Synthetic", "Test");
        if (!closed.reason().equals("closed")) throw new AssertionError("cancel");
        frame = StreamWire.read(new ByteArrayInputStream(out.toByteArray()));
        if (!new JSONObject(new String(frame.payload(), StandardCharsets.UTF_8)).getString("method").equals("cancel")) throw new AssertionError("cancel request");
        var busy = SillConnection.homePairingCommand(response("{\"ok\":false,\"reason\":\"busy\",\"retryAfter\":99}"), out, "ask", "Synthetic", "Test");
        if (!busy.reason().equals("busy") || busy.retryMillis() != 10000) throw new AssertionError("retry bound");
        try { SillConnection.homePairingCommand(response("{\"ok\":true,\"method\":\"cable\"}"), out, "ask", "Synthetic", "Test"); throw new AssertionError("unverified cable success"); }
        catch (IOException expected) { }
        System.out.println("PASS synthetic ask, cancel, bounded busy retry and unverified cable rejection");
    }
}
