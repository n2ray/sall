package io.github.n2ray.sall;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class ConnectionPreviewStoreTest {
    private static final class Fake implements ConnectionPreviewStore.Backend {
        final Map<String, SecretKey> keys = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final Set<String> markers = new HashSet<>();
        boolean unlocked = true, failKey, needsAuth, invalidKey, failDelete, failDataDelete, failWrite, failMark, lockBeforeCommit;
        java.util.concurrent.CountDownLatch writeEntered, continueWrite;
        public boolean available() { return unlocked; }
        public SecretKey key(String id, boolean create) throws GeneralSecurityException {
            if (needsAuth) throw new GeneralSecurityException(new UserNotAuthenticatedException());
            if (failKey) throw invalidKey ? new Permanent() : new GeneralSecurityException();
            if (create && !keys.containsKey(id)) { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); keys.put(id, new SecretKeySpec(b, "AES")); }
            return keys.get(id);
        }
        public void deleteKey(String id) throws GeneralSecurityException { if (failDelete) throw new GeneralSecurityException(); keys.remove(id); }
        public boolean tombstone(String id) { return markers.contains(id); }
        public void mark(String id) throws IOException { if (failMark) throw new IOException(); markers.add(id); }
        public void unmark(String id) { markers.remove(id); }
        public byte[] read(String id, int maximum) throws IOException {
            byte[] data = files.get(id); if (data != null && data.length > maximum) throw new IOException();
            return data == null ? null : data.clone();
        }
        public boolean write(String id, byte[] encrypted, BooleanSupplier mayCommit) throws IOException {
            if (failWrite) throw new IOException();
            if (writeEntered != null) {
                writeEntered.countDown();
                try { if (!continueWrite.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Write wait timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            if (lockBeforeCommit) { unlocked = false; lockBeforeCommit = false; }
            if (!mayCommit.getAsBoolean()) return false;
            files.put(id, encrypted.clone()); return true;
        }
        public void delete(String id) throws IOException { if (failDataDelete) throw new IOException(); files.remove(id); }
        public boolean permanentlyInvalid(Throwable e) { return e instanceof Permanent; }
    }
    private static final class Permanent extends GeneralSecurityException {}
    private static final class UserNotAuthenticatedException extends GeneralSecurityException {}
    private static boolean contains(byte[] data, byte[] needle) {
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
    private static void simulateProcessRestart() throws Exception {
        for (String name : new String[]{"REVISIONS", "BINDINGS", "BLOCKED"}) {
            java.lang.reflect.Field field = ConnectionPreviewStore.class.getDeclaredField(name); field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) map.clear(); else ((Set<?>) value).clear();
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] pin = new byte[32], other = new byte[32]; other[31] = 1;
        byte[] synthetic = "synthetic-private-image-data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String id = ConnectionPreviewPolicy.fingerprintName(pin), otherId = ConnectionPreviewPolicy.fingerprintName(other);
        Fake backend = new Fake(); ConnectionPreviewStore s = new ConnectionPreviewStore(backend);
        s.bind(1, pin); s.context(true, true);
        ConnectionPreviewPolicy.Ticket ticket = s.begin(1, pin, true);
        assert s.save(ticket, 1, pin, true, true, synthetic);
        byte[] first = backend.files.get(id).clone();
        assert !contains(first, synthetic) && Arrays.equals(synthetic, s.load(pin, true));
        backend.needsAuth = true; assert s.load(pin, true) == null && s.needsAuthentication();
        backend.needsAuth = false; assert Arrays.equals(synthetic, s.load(pin, true)) && !s.needsAuthentication();
        ticket = s.begin(1, pin, true); assert s.save(ticket, 1, pin, true, true, synthetic);
        assert !Arrays.equals(first, backend.files.get(id)); // random GCM nonces
        assert s.load(pin, false) == null;
        backend.keys.put(otherId, backend.keys.get(id)); backend.files.put(otherId, backend.files.get(id).clone());
        assert s.load(other, true) == null; // full certificate fingerprint is authenticated as AAD
        byte[] valid = backend.files.get(id).clone(); backend.files.get(id)[valid.length - 1] ^= 1;
        assert s.load(pin, true) == null; backend.files.put(id, valid);
        ticket = s.begin(1, pin, true); backend.lockBeforeCommit = true;
        assert !s.save(ticket, 1, pin, true, true, "new synthetic".getBytes());
        assert s.load(pin, true) == null;
        backend.unlocked = true; s.context(true, true); assert Arrays.equals(synthetic, s.load(pin, true));
        ticket = s.begin(1, pin, true); assert !s.save(ticket, 1, pin, true, false, synthetic);
        s.context(true, false); assert s.load(pin, true) == null && s.begin(1, pin, true) == null;
        s.context(true, true); backend.failKey = true;
        ticket = s.begin(1, pin, true); assert !s.save(ticket, 1, pin, true, true, synthetic);
        backend.failKey = false; assert Arrays.equals(synthetic, s.load(pin, true));

        ticket = s.begin(1, pin, true); backend.failDelete = true;
        assert !s.forget(pin) && backend.markers.contains(id);
        assert !s.save(ticket, 1, pin, true, true, synthetic);
        ConnectionPreviewStore restarted = new ConnectionPreviewStore(backend);
        restarted.bind(2, pin); restarted.context(true, true); assert restarted.load(pin, true) == null;
        backend.failDelete = false; backend.failWrite = true;
        ticket = restarted.begin(2, pin, true); assert !restarted.save(ticket, 2, pin, true, true, synthetic);
        assert backend.markers.contains(id) && restarted.load(pin, true) == null;
        backend.failWrite = false; ticket = restarted.begin(2, pin, true);
        assert restarted.save(ticket, 2, pin, true, true, synthetic) && !backend.markers.contains(id);
        assert Arrays.equals(synthetic, restarted.load(pin, true));
        backend.failKey = true; backend.invalidKey = true;
        assert restarted.load(pin, true) == null && backend.markers.contains(id) && !backend.files.containsKey(id);

        Fake shared = new Fake(); ConnectionPreviewStore oldInstance = new ConnectionPreviewStore(shared);
        ConnectionPreviewStore newInstance = new ConnectionPreviewStore(shared);
        oldInstance.bind(3, pin); oldInstance.context(true, true);
        newInstance.bind(4, pin); newInstance.context(true, true);
        ConnectionPreviewPolicy.Ticket stale = oldInstance.begin(3, pin, true);
        assert newInstance.forget(pin);
        assert !oldInstance.save(stale, 3, pin, true, true, synthetic);
        assert oldInstance.begin(3, pin, true) == null; // other instances cannot re-use a forgotten binding
        assert shared.markers.contains(id) && !shared.files.containsKey(id);
        newInstance.bind(5, pin); newInstance.context(true, true);
        ConnectionPreviewPolicy.Ticket fresh = newInstance.begin(5, pin, true);
        assert newInstance.save(fresh, 5, pin, true, true, synthetic);
        assert Arrays.equals(synthetic, newInstance.load(pin, true));
        oldInstance.bind(8, pin); oldInstance.context(true, true);
        ConnectionPreviewPolicy.Ticket older = oldInstance.begin(8, pin, true);
        ConnectionPreviewPolicy.Ticket newer = newInstance.begin(5, pin, true);
        byte[] newerImage = "newer synthetic image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assert newInstance.save(newer, 5, pin, true, true, newerImage);
        assert !oldInstance.save(older, 8, pin, true, true, synthetic);
        assert Arrays.equals(newerImage, newInstance.load(pin, true));

        // Marker failure still removes both durable recovery paths before reporting success.
        shared.failMark = true;
        assert newInstance.forget(pin) && !shared.keys.containsKey(id) && !shared.files.containsKey(id);
        simulateProcessRestart();
        ConnectionPreviewStore afterRestart = new ConnectionPreviewStore(shared);
        afterRestart.bind(6, pin); afterRestart.context(true, true); assert afterRestart.load(pin, true) == null;

        // If every durable path fails, forget fails; callers must not report success or remove the identity.
        shared.failMark = false; fresh = afterRestart.begin(6, pin, true);
        assert afterRestart.save(fresh, 6, pin, true, true, synthetic);
        shared.failMark = true; shared.failDelete = true; shared.failDataDelete = true;
        assert !afterRestart.forget(pin) && afterRestart.load(pin, true) == null;
        simulateProcessRestart();
        ConnectionPreviewStore unresolved = new ConnectionPreviewStore(shared);
        unresolved.bind(7, pin); unresolved.context(true, true);
        assert Arrays.equals(synthetic, unresolved.load(pin, true)); // failure is not a deletion receipt
        shared.failMark = shared.failDelete = shared.failDataDelete = false;
        assert unresolved.forget(pin);
        unresolved.bind(9, pin); unresolved.context(true, true); fresh = unresolved.begin(9, pin, true);
        assert unresolved.save(fresh, 9, pin, true, true, synthetic);

        // Backgrounding becomes visible to a write already waiting to commit.
        Fake racing = new Fake(); ConnectionPreviewStore raceStore = new ConnectionPreviewStore(racing);
        byte[] racePin = new byte[32]; racePin[0] = 42;
        raceStore.bind(10, racePin); raceStore.context(true, true);
        var raceTicket = raceStore.begin(10, racePin, true);
        racing.writeEntered = new java.util.concurrent.CountDownLatch(1);
        racing.continueWrite = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread save = new Thread(() -> result.set(raceStore.save(raceTicket, 10, racePin, true, true, synthetic)));
        Thread pause = new Thread(() -> raceStore.context(true, false));
        save.start();
        try {
            assert racing.writeEntered.await(5, java.util.concurrent.TimeUnit.SECONDS);
            pause.start();
            long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (pause.getState() != Thread.State.BLOCKED && System.nanoTime() < end) Thread.yield();
            assert pause.getState() == Thread.State.BLOCKED;
        } finally { racing.continueWrite.countDown(); }
        save.join(5000); pause.join(5000);
        assert !save.isAlive() && !pause.isAlive() && !result.get();
        assert !racing.files.containsKey(ConnectionPreviewPolicy.fingerprintName(racePin));
        System.out.println("Encrypted preview AAD, lock, tombstone and recovery checks passed (synthetic backend)");
    }
}
