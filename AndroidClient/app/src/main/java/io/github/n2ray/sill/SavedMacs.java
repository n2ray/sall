package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Identity rules shared by storage, pairing and the saved connection list. */
final class SavedMacs {
    static final String PREFIX = "savedMac.v1:";
    static boolean manuallyEnteredRoute(boolean typedByUser, Endpoint requested, Endpoint actual) {
        return typedByUser && requested.equals(actual);
    }
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

    static final double REMEMBER_FOR = 90 * 24 * 3600;
    static final int REMEMBER_CAP = 4;
    record Advertised(Endpoint endpoint, String kind, boolean implicitPort) {
        Advertised(Endpoint endpoint, String kind) { this(endpoint, kind, false); }
        Advertised withDefaultPort(int port) {
            return implicitPort && port > 0 ? new Advertised(new Endpoint(endpoint.host(), port), kind, true) : this;
        }
    }
    record Remembered(Advertised address, double listedAt) {}
    record Mac(Identity identity, List<Endpoint> typedAddresses, Endpoint lastWorked, double infoIssuedAt,
               List<Endpoint> discoveredAddresses, List<Advertised> advertised, List<Remembered> remembered, double listedAt) {
        Mac(Identity identity, List<Endpoint> addresses, Endpoint lastWorked) { this(identity, addresses, lastWorked, 0); }
        Mac(Identity identity, List<Endpoint> addresses, Endpoint lastWorked, double issued) {
            this(identity, addresses, lastWorked, issued, List.of(), List.of(), List.of(), issued);
        }
        Mac {
            if (!Double.isFinite(infoIssuedAt) || infoIssuedAt < 0 || !Double.isFinite(listedAt) || listedAt < 0)
                throw new IllegalArgumentException("Invalid info timestamp");
            typedAddresses = List.copyOf(new java.util.LinkedHashSet<>(typedAddresses));
            discoveredAddresses = List.copyOf(new java.util.LinkedHashSet<>(discoveredAddresses));
            advertised = List.copyOf(advertised); remembered = List.copyOf(remembered);
        }
        List<Endpoint> currentAddresses() {
            java.util.LinkedHashSet<Endpoint> result = new java.util.LinkedHashSet<>();
            for (Advertised a : advertised) result.add(a.endpoint());
            result.addAll(typedAddresses); result.addAll(discoveredAddresses);
            return List.copyOf(result);
        }
        List<Endpoint> addresses() { return addresses(System.currentTimeMillis() / 1000.0); }
        List<Endpoint> addresses(double now) {
            java.util.LinkedHashSet<Endpoint> result = new java.util.LinkedHashSet<>();
            for (Advertised a : advertised) result.add(a.endpoint());
            result.addAll(typedAddresses); result.addAll(discoveredAddresses);
            for (Remembered r : remembered) if (now - r.listedAt() <= REMEMBER_FOR) result.add(r.address().endpoint());
            return List.copyOf(result);
        }
        Endpoint preferred() { return lastWorked != null && addresses().contains(lastWorked) ? lastWorked : addresses().get(0); }
        String displayName() { return identity.name().isEmpty() ? "Mac · " + identity.macId() : identity.name(); }
    }

    static List<Remembered> remembering(Mac old, List<Advertised> current, boolean replace, double now) {
        List<Remembered> offered = new ArrayList<>();
        if (replace) for (Advertised a : old.advertised()) offered.add(new Remembered(a, old.listedAt()));
        offered.addAll(old.remembered());
        Map<String, Remembered> newest = new LinkedHashMap<>();
        for (Remembered r : offered) {
            Endpoint e = r.address().endpoint();
            double at = Math.min(now, r.listedAt());
            if (!r.address().kind().equals("vpn") || now - at > REMEMBER_FOR
                    || current.stream().anyMatch(a -> a.endpoint().legacyKey().equalsIgnoreCase(e.legacyKey()))) continue;
            String key = e.legacyKey().toLowerCase(java.util.Locale.ROOT);
            if (!newest.containsKey(key) || newest.get(key).listedAt() < at) newest.put(key, new Remembered(r.address(), at));
        }
        return newest.values().stream().sorted(java.util.Comparator.comparingDouble(Remembered::listedAt).reversed())
                .limit(REMEMBER_CAP).toList();
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
                // Once a v2 record exists, rollback pins must not resurrect expired advertised paths.
                if (!preferences.containsKey(PREFIX + identity.macId() + ":addressOrigins"))
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
                String issued = string(preferences, base + "infoIssuedAt");
                Endpoint worked = last.isEmpty() ? null : Endpoint.fromLegacyKey(last);
                double timestamp = issued.isEmpty() ? 0 : Double.parseDouble(issued);
                if (preferences.get(base + "addressOrigins") instanceof String origins) {
                    List<Endpoint> typed = new ArrayList<>(), discovered = new ArrayList<>();
                    List<Advertised> advertised = new ArrayList<>(); List<Remembered> remembered = new ArrayList<>();
                    for (String row : origins.split("\\n")) {
                        if (row.isBlank()) continue;
                        String[] parts = row.split("\\t", -1);
                        Endpoint endpoint = Endpoint.fromLegacyKey(parts[3]);
                        switch (parts[0]) {
                            case "typed" -> typed.add(endpoint);
                            case "discovered" -> discovered.add(endpoint);
                            case "advertised" -> advertised.add(new Advertised(endpoint, parts[1], parts.length > 4 && parts[4].equals("implicit")));
                            case "remembered" -> remembered.add(new Remembered(new Advertised(endpoint, parts[1], parts.length > 4 && parts[4].equals("implicit")), Double.parseDouble(parts[2])));
                            default -> throw new IllegalArgumentException("Unknown address origin");
                        }
                    }
                    String listed = string(preferences, base + "addressesListedAt");
                    records.add(new Mac(identity, typed, worked, timestamp, discovered, advertised, remembered,
                            listed.isEmpty() ? timestamp : Double.parseDouble(listed)));
                } else records.add(new Mac(identity, addresses, worked, timestamp));
            } catch (IllegalArgumentException | ClassCastException | NullPointerException | IndexOutOfBoundsException ignored) { }
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
            result.put(base + "infoIssuedAt", Double.toString(mac.infoIssuedAt()));
            result.put(base + "addressesListedAt", Double.toString(mac.listedAt()));
            StringBuilder origins = new StringBuilder();
            for (Endpoint e : mac.typedAddresses()) origins.append("typed\t\t0\t").append(e.legacyKey()).append('\n');
            for (Endpoint e : mac.discoveredAddresses()) origins.append("discovered\t\t0\t").append(e.legacyKey()).append('\n');
            for (Advertised a : mac.advertised()) origins.append("advertised\t").append(a.kind()).append("\t0\t").append(a.endpoint().legacyKey()).append('\t').append(a.implicitPort() ? "implicit" : "").append('\n');
            for (Remembered r : mac.remembered()) origins.append("remembered\t").append(r.address().kind()).append('\t').append(r.listedAt()).append('\t').append(r.address().endpoint().legacyKey()).append('\t').append(r.address().implicitPort() ? "implicit" : "").append('\n');
            result.put(base + "addressOrigins", origins.toString());
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
        List<Endpoint> addresses = new ArrayList<>(old.typedAddresses()); addresses.addAll(incoming.typedAddresses());
        Identity identity = old.identity().recognitionKey().isEmpty() && old.identity().name().isEmpty() ? incoming.identity() : old.identity();
        merged.put(identity.macId(), new Mac(identity, addresses, old.lastWorked() != null ? old.lastWorked() : incoming.lastWorked(),
                Math.max(old.infoIssuedAt(), incoming.infoIssuedAt()), old.discoveredAddresses(), old.advertised(), old.remembered(), old.listedAt()));
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
