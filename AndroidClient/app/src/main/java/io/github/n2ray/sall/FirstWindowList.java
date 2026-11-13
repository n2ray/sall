package io.github.n2ray.sall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Bounded pre-session messages; the transport enforces the absolute deadline on every read. */
final class FirstWindowList {
    static final long TIMEOUT_MS = 10_000;
    interface Reader { StreamWire.Message read(long deadlineNanos) throws Exception; }
    interface Validator { void validate(StreamWire.Message message) throws Exception; }
    static List<StreamWire.Message> await(Reader reader, Validator validator) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000;
        List<StreamWire.Message> messages = new ArrayList<>();
        long bytes = 0;
        while (true) {
            if (System.nanoTime() >= deadline) throw new java.net.SocketTimeoutException("Mac did not send its window list");
            StreamWire.Message message = reader.read(deadline);
            bytes += message.payload().length;
            if (bytes > 32L << 20 || messages.size() >= 64) throw new IOException("Too many messages before Mac window list");
            if (message.kind() == 2 || message.kind() == 22) validator.validate(message);
            messages.add(message);
            if (message.kind() == 2) return List.copyOf(messages);
        }
    }
}
