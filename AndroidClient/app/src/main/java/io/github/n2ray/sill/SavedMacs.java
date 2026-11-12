package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Identity rules shared by storage, pairing and the saved connection list. */
final class SavedMacs {
    static final String PREFIX = "savedMac.v1:";
    record Endpoint(String host, int port) {
        Endpoint {
            if (host == null || host.isBlank() || host.length() > 253 || port < 1 || port > 65535
                    || host.codePoints().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid saved address");
        }
        String legacyKey() { return "pin:" + host + ":" + port; }
        String display() { return (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }
        static Endpoint fromLegacyKey(String key) {
            if (key == null || !key.startsWith("pin:")) throw new IllegalArgumentException();
            int split = key.lastIndexOf(':');
            if (split <= 4) throw new IllegalArgumentException();
            return new Endpoint(key.substring(4, split), Integer.parseInt(key.substring(split + 1)));
        }
    }

    record Identity(String macId, String fingerprint, String name, String recognitionKey) {
        Identity {
            byte[] pin = decodePin(fingerprint);
            if (!PairLink.macId(pin).equals(macId)) throw new IllegalArgumentException("Mac identity mismatch");
            fingerprint = encode(pin);
            name = safeName(name);
            if (recognitionKey == null) recognitionKey = "";
            if (!recognitionKey.isEmpty()) {
                byte[] key = Base64.getUrlDecoder().decode(recognitionKey);
                if (key.length != 32) throw new IllegalArgumentException("Invalid recognition key");
                recognitionKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
            }
        }
        byte[] pin() { return decodePin(fingerprint); }
        static Identity migrated(byte[] pin) { return new Identity(PairLink.macId(pin), encode(pin), "", ""); }
        // Call only after TLS identity and the Mac's pairing proof have been checked.
        static Identity paired(byte[] pin, String macId, String name, String recognitionKey) {
            if (recognitionKey == null || recognitionKey.isEmpty()) throw new IllegalArgumentException("Missing recognition key");
            return new Identity(macId, encode(pin), name, recognitionKey);
        }
        static Identity pairingResult(byte[] pin, String macId, String name, String recognitionKey) {
            String expected = PairLink.macId(pin);
            if (macId != null && !expected.equals(macId)) throw new IllegalArgumentException("Mac identity mismatch");
            if (recognitionKey != null) {
                if (recognitionKey.isEmpty() || Base64.getUrlDecoder().decode(recognitionKey).length != 32)
                    throw new IllegalArgumentException("Invalid recognition key");
            }
            // Older Android pairings only required proof_M. Missing metadata keeps that pin-only path.
            if (macId == null || recognitionKey == null) return migrated(pin);
            return paired(pin, macId, name, recognitionKey);
        }
    }

    record Mac(Identity identity, List<Endpoint> addresses, Endpoint lastWorked) {
        Mac {
            addresses = List.copyOf(new java.util.LinkedHashSet<>(addresses));
            if (addresses.isEmpty()) throw new IllegalArgumentException("Missing saved address");
            if (lastWorked != null && !addresses.contains(lastWorked)) lastWorked = null;
        }
        Endpoint preferred() { return lastWorked != null ? lastWorked : addresses.get(0); }
        String displayName() { return identity.name().isEmpty() ? "Mac · " + identity.macId() : identity.name(); }
    }

    static List<Mac> migrate(List<Mac> saved, Map<String, ?> preferences) {
        Map<String, Mac> merged = new LinkedHashMap<>();
        for (Mac mac : saved) merge(merged, mac);
        List<String> keys = new ArrayList<>(preferences.keySet());
        java.util.Collections.sort(keys);
        for (String key : keys) {
            if (!key.startsWith("pin:") || !(preferences.get(key) instanceof String encoded)) continue;
            try {
                Identity identity = Identity.migrated(decodePin(encoded));
                merge(merged, new Mac(identity, List.of(Endpoint.fromLegacyKey(key)), null));
            } catch (IllegalArgumentException ignored) { } // Keep malformed legacy entries untouched in storage.
        }
        return List.copyOf(merged.values());
    }

    static List<Mac> read(Map<String, ?> preferences) {
        List<Mac> records = new ArrayList<>();
        for (String key : preferences.keySet()) {
            if (!key.startsWith(PREFIX) || !key.endsWith(":pin")) continue;
            String id = key.substring(PREFIX.length(), key.length() - 4), base = PREFIX + id + ":";
            try {
                Identity identity = new Identity(id, (String) preferences.get(key),
                        string(preferences, base + "name"), string(preferences, base + "recognition"));
                List<Endpoint> addresses = new ArrayList<>();
                if (preferences.get(base + "addresses") instanceof java.util.Set<?> values)
                    for (Object value : values) if (value instanceof String address)
                        addresses.add(Endpoint.fromLegacyKey(address));
                addresses.sort(java.util.Comparator.comparing(Endpoint::legacyKey));
                String last = string(preferences, base + "lastWorked");
                records.add(new Mac(identity, addresses, last.isEmpty() ? null : Endpoint.fromLegacyKey(last)));
            } catch (IllegalArgumentException | ClassCastException | NullPointerException ignored) { }
        }
        records.sort(java.util.Comparator.comparing(mac -> mac.identity().macId()));
        return migrate(records, preferences);
    }

    static Map<String, Object> encode(List<Mac> records) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Mac mac : records) {
            String base = PREFIX + mac.identity().macId() + ":";
            result.put(base + "pin", mac.identity().fingerprint());
            result.put(base + "name", mac.identity().name());
            result.put(base + "recognition", mac.identity().recognitionKey());
            java.util.Set<String> addresses = new java.util.HashSet<>();
            for (Endpoint endpoint : mac.addresses()) addresses.add(endpoint.legacyKey());
            result.put(base + "addresses", addresses);
            result.put(base + "lastWorked", mac.lastWorked() == null ? "" : mac.lastWorked().legacyKey());
        }
        return result;
    }

    private static String string(Map<String, ?> values, String key) {
        return values.get(key) instanceof String text ? text : "";
    }

    private static void merge(Map<String, Mac> merged, Mac incoming) {
        Mac old = merged.get(incoming.identity().macId());
        if (old == null) { merged.put(incoming.identity().macId(), incoming); return; }
        // An 80-bit ID collision must never replace the full certificate pin.
        if (!old.identity().fingerprint().equals(incoming.identity().fingerprint()))
            throw new IllegalArgumentException("Conflicting saved identity");
        List<Endpoint> addresses = new ArrayList<>(old.addresses()); addresses.addAll(incoming.addresses());
        Identity identity = old.identity().recognitionKey().isEmpty() ? incoming.identity() : old.identity();
        merged.put(identity.macId(), new Mac(identity, addresses, old.lastWorked() != null ? old.lastWorked() : incoming.lastWorked()));
    }

    static byte[] decodePin(String encoded) {
        byte[] pin = Base64.getDecoder().decode(encoded);
        if (pin.length != 32) throw new IllegalArgumentException("Invalid saved fingerprint");
        return pin;
    }
    static String encode(byte[] pin) {
        if (pin == null || pin.length != 32) throw new IllegalArgumentException("Invalid Mac fingerprint");
        return Base64.getEncoder().encodeToString(pin);
    }
    private static String safeName(String name) {
        if (name == null) return "";
        StringBuilder result = new StringBuilder();
        name.codePoints().filter(cp -> !Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT
                && Character.getType(cp) != Character.SURROGATE).limit(64).forEach(result::appendCodePoint);
        return result.toString().trim();
    }
}
