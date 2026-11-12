package io.github.n2ray.sill;

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
        for (SavedMacs.Mac mac : records) if (mac.identity().macId().equals(identity.macId())) {
            if (!mac.identity().fingerprint().equals(identity.fingerprint()))
                throw new IllegalArgumentException("Conflicting saved identity");
            addresses.addAll(mac.addresses());
            if (identity.recognitionKey().isEmpty() && !mac.identity().recognitionKey().isEmpty())
                savedIdentity = mac.identity();
        }
        records.removeIf(mac -> mac.identity().macId().equals(identity.macId()));
        addresses.add(endpoint);
        records.add(new SavedMacs.Mac(savedIdentity, addresses, endpoint));
        // The old endpoint pin remains a rollback-compatible authoritative choice after re-pairing.
        write(records, preferences.edit().putString(endpoint.legacyKey(), identity.fingerprint())).apply();
    }

    synchronized void connected(byte[] pin, SavedMacs.Endpoint endpoint) {
        List<SavedMacs.Mac> records = new ArrayList<>(all());
        for (int i = 0; i < records.size(); i++) {
            SavedMacs.Mac mac = records.get(i);
            if (!java.security.MessageDigest.isEqual(mac.identity().pin(), pin)) continue;
            List<SavedMacs.Endpoint> addresses = new ArrayList<>(mac.addresses()); addresses.add(endpoint);
            records.set(i, new SavedMacs.Mac(mac.identity(), addresses, endpoint));
            write(records, preferences.edit()).apply(); return;
        }
    }

    synchronized void forget(SavedMacs.Identity identity) {
        SharedPreferences.Editor edit = preferences.edit();
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
