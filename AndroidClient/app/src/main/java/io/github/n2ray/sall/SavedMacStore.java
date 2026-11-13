package io.github.n2ray.sall;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Keeps legacy address pins intact until the user explicitly forgets their Mac. */
final class SavedMacStore {
    private final SharedPreferences preferences;
    SavedMacStore(SharedPreferences preferences) {
        this.preferences = preferences;
        write(SavedMacs.read(preferences.getAll()), preferences.edit()).apply();
    }

    synchronized List<SavedMacs.Mac> all() { return SavedMacs.read(preferences.getAll()); }

    synchronized SavedMacs.Identity recognized(String tag) {
        SavedMacs.Identity match = null;
        for (SavedMacs.Mac mac : all()) {
            String encoded = mac.identity().recognitionKey();
            if (encoded.isEmpty()) continue;
            byte[] key;
            try { key = java.util.Base64.getUrlDecoder().decode(encoded); }
            catch (IllegalArgumentException e) { continue; }
            if (!RecognitionTag.matches(tag, key)) continue;
            if (match != null) return null;
            match = mac.identity();
        }
        return match;
    }

    synchronized byte[] recognizedPin(SavedMacs.Identity expected, String tag) {
        SavedMacs.Identity matched = recognized(tag);
        if (matched == null || !matched.macId().equals(expected.macId())
                || !matched.fingerprint().equals(expected.fingerprint())) return null;
        return matched.pin();
    }

    synchronized byte[] pin(SavedMacs.Endpoint endpoint) {
        Object legacy = preferences.getAll().get(endpoint.legacyKey());
        if (legacy instanceof String encoded) {
            try { return SavedMacs.decodePin(encoded); } catch (IllegalArgumentException ignored) { return null; }
        }
        byte[] found = null;
        for (SavedMacs.Mac mac : all()) if (mac.addresses().contains(endpoint)) {
            byte[] next = mac.identity().pin();
            if (found != null && !java.security.MessageDigest.isEqual(found, next)) return null;
            found = next;
        }
        return found;
    }

    synchronized byte[] pin(SavedMacs.Identity identity, SavedMacs.Endpoint endpoint) {
        for (SavedMacs.Mac mac : all()) if (mac.identity().macId().equals(identity.macId())
                && mac.identity().fingerprint().equals(identity.fingerprint()) && mac.addresses().contains(endpoint))
            return mac.identity().pin();
        return null;
    }

    synchronized void paired(SavedMacs.Identity identity, SavedMacs.Endpoint endpoint) {
        List<SavedMacs.Mac> records = new ArrayList<>(all());
        List<SavedMacs.Endpoint> addresses = new ArrayList<>();
        SavedMacs.Identity savedIdentity = identity;
        double issuedAt = 0;
        SavedMacs.Mac previous = null;
        for (SavedMacs.Mac mac : records) if (mac.identity().macId().equals(identity.macId())) {
            if (!mac.identity().fingerprint().equals(identity.fingerprint()))
                throw new IllegalArgumentException("Conflicting saved identity");
            addresses.addAll(mac.typedAddresses());
            previous = mac;
            issuedAt = mac.infoIssuedAt();
            if (identity.recognitionKey().isEmpty() && (!mac.identity().recognitionKey().isEmpty() || !mac.identity().name().isEmpty()))
                savedIdentity = mac.identity();
        }
        records.removeIf(mac -> mac.identity().macId().equals(identity.macId()));
        addresses.add(endpoint);
        records.add(new SavedMacs.Mac(savedIdentity, addresses, endpoint, issuedAt,
                previous == null ? List.of() : previous.discoveredAddresses(), previous == null ? List.of() : previous.advertised(),
                previous == null ? List.of() : previous.remembered(), previous == null ? System.currentTimeMillis() / 1000.0 : previous.listedAt()));
        // The old endpoint pin remains a rollback-compatible authoritative choice after re-pairing.
        write(records, preferences.edit().putString(endpoint.legacyKey(), identity.fingerprint())).apply();
    }

    synchronized void connected(byte[] pin, SavedMacs.Endpoint endpoint) { connected(pin, endpoint, false); }

    synchronized void connected(byte[] pin, SavedMacs.Endpoint endpoint, boolean typed) {
        List<SavedMacs.Mac> records = new ArrayList<>(all());
        for (int i = 0; i < records.size(); i++) {
            SavedMacs.Mac mac = records.get(i);
            if (!java.security.MessageDigest.isEqual(mac.identity().pin(), pin)) continue;
            List<SavedMacs.Endpoint> manual = new ArrayList<>(mac.typedAddresses());
            List<SavedMacs.Endpoint> discovered = new ArrayList<>(mac.discoveredAddresses());
            if (typed) manual.add(endpoint);
            else if (!mac.addresses().contains(endpoint)) discovered.add(endpoint);
            records.set(i, new SavedMacs.Mac(mac.identity(), manual, endpoint, mac.infoIssuedAt(), discovered,
                    mac.advertised(), mac.remembered(), mac.listedAt()));
            write(records, preferences.edit()).apply(); return;
        }
        // Only an explicitly typed and successfully authenticated legacy endpoint may repair its own damaged record.
        if (!typed) return;
        Map<String, ?> values = preferences.getAll();
        if (pin == null || pin.length != 32) return;
        String id = PairLink.macId(pin), base = SavedMacs.PREFIX + id + ":";
        if (records.stream().anyMatch(mac -> mac.identity().macId().equals(id))) return;
        Object legacy = values.get(endpoint.legacyKey()), stored = values.get(base + "pin");
        Object origins = values.get(base + "addressOrigins"), issued = values.get(base + "infoIssuedAt");
        if (!(legacy instanceof String oldPin) || !(stored instanceof String recordPin)
                || !(origins instanceof String) || !(issued instanceof String timestamp)) return;
        try {
            if (!java.security.MessageDigest.isEqual(pin, SavedMacs.decodePin(oldPin))
                    || !java.security.MessageDigest.isEqual(pin, SavedMacs.decodePin(recordPin))) return;
            double at = Double.parseDouble(timestamp);
            if (!Double.isFinite(at) || at < 0) return;
            String name = values.get(base + "name") instanceof String text ? text : "";
            String recognition = values.get(base + "recognition") instanceof String text ? text : "";
            SavedMacs.Identity identity = new SavedMacs.Identity(id, recordPin, name, recognition);
            records.add(new SavedMacs.Mac(identity, List.of(endpoint), endpoint, at));
            write(records, preferences.edit()).apply();
        } catch (IllegalArgumentException ignored) { }
    }

    // Call only after SignedMacInfo.verify has authenticated the payload against this TLS session.
    synchronized boolean refresh(byte[] sessionPin, SignedMacInfo.Update info) {
        return refresh(sessionPin, info, System.currentTimeMillis() / 1000.0);
    }

    synchronized boolean refresh(byte[] sessionPin, SignedMacInfo.Update info, double now) {
        if (!Double.isFinite(now) || now < 0) return false;
        if (sessionPin == null || sessionPin.length != 32 || !PairLink.macId(sessionPin).equals(info.macId())) return false;
        List<SavedMacs.Mac> records = new ArrayList<>(all());
        for (int i = 0; i < records.size(); i++) {
            SavedMacs.Mac mac = records.get(i);
            if (!mac.identity().macId().equals(info.macId()) || !java.security.MessageDigest.isEqual(mac.identity().pin(), sessionPin)
                    || info.issuedAt() <= mac.infoIssuedAt()) continue;
            SavedMacs.Identity next = new SavedMacs.Identity(info.macId(), mac.identity().fingerprint(), info.name(), mac.identity().recognitionKey());
            if (next.name().isEmpty()) next = mac.identity();
            List<SavedMacs.Advertised> previous = mac.advertised().stream().map(a -> a.withDefaultPort(info.remotePort())).toList();
            List<SavedMacs.Remembered> remembered = mac.remembered().stream()
                    .map(r -> new SavedMacs.Remembered(r.address().withDefaultPort(info.remotePort()), r.listedAt())).toList();
            SavedMacs.Mac rebased = new SavedMacs.Mac(mac.identity(), mac.typedAddresses(), mac.lastWorked(), mac.infoIssuedAt(),
                    mac.discoveredAddresses(), previous, remembered, mac.listedAt());
            List<SavedMacs.Advertised> advertised = new ArrayList<>();
            for (int j = 0; j < info.addresses().size(); j++)
                advertised.add(new SavedMacs.Advertised(info.addresses().get(j), info.kinds().get(j), info.implicitPorts().get(j)));
            boolean replace = info.remoteAccess() && !advertised.isEmpty();
            if (!replace) advertised = previous;
            records.set(i, new SavedMacs.Mac(next, mac.typedAddresses(), mac.lastWorked(), info.issuedAt(),
                    // A successful Bonjour path is provisional; the signed list owns advertised routes.
                    replace ? List.of() : mac.discoveredAddresses(), advertised, SavedMacs.remembering(rebased, advertised, replace, now),
                    replace ? now : mac.listedAt()));
            write(records, preferences.edit()).apply();
            return true;
        }
        return false;
    }

    synchronized void forget(SavedMacs.Identity identity) {
        SharedPreferences.Editor edit = preferences.edit();
        edit.remove("connGroup:" + identity.fingerprint());
        String prefix = SavedMacs.PREFIX + identity.macId() + ":";
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix)) edit.remove(entry.getKey());
            if (entry.getKey().startsWith("pin:") && entry.getValue() instanceof String pin) {
                try { if (java.security.MessageDigest.isEqual(identity.pin(), SavedMacs.decodePin(pin))) edit.remove(entry.getKey()); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        edit.apply();
    }

    /** A removal notice may revoke only the full certificate identity of its TLS session. */
    synchronized boolean forgetPin(byte[] pin) {
        if (pin == null || pin.length != 32) return false;
        for (SavedMacs.Mac mac : all()) if (java.security.MessageDigest.isEqual(pin, mac.identity().pin())) {
            forget(mac.identity());
            return true;
        }
        return false;
    }

    private static SharedPreferences.Editor write(List<SavedMacs.Mac> records, SharedPreferences.Editor edit) {
        for (Map.Entry<String, Object> entry : SavedMacs.encode(records).entrySet()) {
            if (entry.getValue() instanceof String value) edit.putString(entry.getKey(), value);
            else {
                java.util.Set<String> values = new java.util.HashSet<>();
                for (Object value : (java.util.Set<?>) entry.getValue()) values.add((String) value);
                edit.putStringSet(entry.getKey(), values);
            }
        }
        return edit;
    }
}
