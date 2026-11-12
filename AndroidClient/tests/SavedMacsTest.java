package io.github.n2ray.sill;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SavedMacsTest {
    public static void main(String[] args) {
        byte[] first = new byte[32], second = new byte[32], recognition = new byte[32];
        second[0] = 1; recognition[0] = 4;
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(recognition);
        SavedMacs.Endpoint lan = new SavedMacs.Endpoint("192.168.1.7", 7455);
        SavedMacs.Endpoint vpn = new SavedMacs.Endpoint("100.87.1.2", 7455);
        SavedMacs.Endpoint ipv6 = new SavedMacs.Endpoint("fd7a:115c:a1e0::1234", 7455);
        MemoryPreferences preferences = new MemoryPreferences();
        preferences.values.put(lan.legacyKey(), SavedMacs.encode(first));
        preferences.values.put(vpn.legacyKey(), SavedMacs.encode(first));
        preferences.values.put(ipv6.legacyKey(), SavedMacs.encode(first));
        preferences.values.put("pin:bad:port", "bad");
        preferences.values.put("host", lan.host());
        SavedMacStore store = new SavedMacStore(preferences);
        check(store.all().size() == 1, "Same pin should migrate to one Mac");
        check(store.all().get(0).addresses().size() == 3, "Migration must retain every endpoint including IPv6");
        check(preferences.values.containsKey("pin:bad:port") && preferences.values.containsKey(lan.legacyKey()),
                "Migration must not delete old or malformed records");
        check(new SavedMacStore(preferences).all().equals(store.all()), "Migration must be idempotent");
        check(java.util.Arrays.equals(store.pin(ipv6), first), "Old IPv6 connection remains pinned");

        SavedMacs.Identity one = SavedMacs.Identity.paired(first, PairLink.macId(first), "Office Mac", key);
        SavedMacs.Identity two = SavedMacs.Identity.paired(second, PairLink.macId(second), "Office Mac", key);
        store.paired(one, lan);
        store.paired(two, new SavedMacs.Endpoint("192.168.1.8", 7455));
        check(store.all().size() == 2, "Different pins with the same name must not merge");
        check(store.all().stream().filter(m -> m.identity().equals(one)).findFirst().orElseThrow().addresses().size() == 3,
                "Pairing metadata must preserve previous endpoints");
        store.paired(SavedMacs.Identity.pairingResult(first, null, "Untrusted replacement", null), ipv6);
        SavedMacs.Mac pinOnlyRepaired = new SavedMacStore(preferences).all().stream()
                .filter(m -> m.identity().macId().equals(one.macId())).findFirst().orElseThrow();
        check(pinOnlyRepaired.identity().equals(one), "Pin-only re-pairing must preserve verified name and recognition key");
        check(pinOnlyRepaired.preferred().equals(ipv6), "Pin-only re-pairing must still update the successful endpoint");
        byte[] rotatedRecognition = recognition.clone(); rotatedRecognition[1] = 2;
        SavedMacs.Identity refreshed = SavedMacs.Identity.paired(first, one.macId(), "Renamed Mac",
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rotatedRecognition));
        store.paired(refreshed, lan);
        check(new SavedMacStore(preferences).all().stream().anyMatch(m -> m.identity().equals(refreshed)),
                "A full verified re-pairing must replace metadata");
        store.paired(one, lan);
        store.connected(first, vpn);
        SavedMacs.Mac restored = new SavedMacStore(preferences).all().stream().filter(m -> m.identity().equals(one)).findFirst().orElseThrow();
        check(restored.preferred().equals(vpn), "Successful pinned endpoint must survive reload");
        check(restored.identity().recognitionKey().equals(key), "Verified recognition key must survive reload");
        check(java.util.Arrays.equals(store.pin(lan), first), "Metadata does not change certificate pin");

        store.paired(two, lan);
        check(java.util.Arrays.equals(store.pin(lan), second), "Explicit re-pairing updates the address pin");
        check(store.all().size() == 2, "Reused address must not merge different Macs");
        check(java.util.Arrays.equals(store.pin(one, lan), first), "Choosing a saved Mac must keep its own pin even when its address is reused");
        store.forget(one);
        check(store.pin(one, lan) == null, "A stale saved-Mac dialog cannot reconnect a forgotten identity");
        check(!preferences.values.containsKey(vpn.legacyKey()), "Forget must remove old pins for this Mac");
        check(java.util.Arrays.equals(store.pin(lan), second), "Forget must keep a newer pin at the reused address");
        check(new SavedMacStore(preferences).all().size() == 1, "Forgotten Mac must not reappear through migration");

        reject(() -> SavedMacs.Identity.paired(first, PairLink.macId(second), "Fake", key));
        reject(() -> SavedMacs.Identity.paired(first, PairLink.macId(first), "Fake", ""));
        reject(() -> SavedMacs.Identity.paired(first, PairLink.macId(first), "Fake", "AA"));
        check(SavedMacs.Identity.pairingResult(first, null, "Unverified metadata", null).equals(SavedMacs.Identity.migrated(first)),
                "Missing metadata must preserve the proof-authenticated pin without trusting its name");
        check(SavedMacs.Identity.pairingResult(first, PairLink.macId(first), "Missing key", null).name().isEmpty(),
                "Partial legacy metadata must not establish a trusted name");
        reject(() -> SavedMacs.Identity.pairingResult(first, PairLink.macId(second), "Fake", null));
        reject(() -> SavedMacs.Identity.pairingResult(first, null, "Fake", "AA"));
        reject(() -> SavedMacs.decodePin("AA=="));
        byte[] collision = first.clone(); collision[31] = 1;
        reject(() -> SavedMacs.migrate(List.of(new SavedMacs.Mac(one, List.of(vpn), null),
                new SavedMacs.Mac(SavedMacs.Identity.migrated(collision), List.of(lan), null)), Map.of()));
        check(SavedMacs.Identity.paired(first, PairLink.macId(first), "Mac\n\u202eName", key).name().equals("MacName"),
                "Stored display names must not contain controls");
        byte[] vectorKey = new byte[32]; java.util.Arrays.fill(vectorKey, (byte) 0x11);
        String vectorEncoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(vectorKey);
        SavedMacStore discoveryStore = new SavedMacStore(new MemoryPreferences());
        SavedMacs.Identity discovered = SavedMacs.Identity.paired(first, PairLink.macId(first), "Trusted", vectorEncoded);
        SavedMacs.Identity duplicate = SavedMacs.Identity.paired(second, PairLink.macId(second), "Other", vectorEncoded);
        String tag = "oaKjpKWmL4V47Ctm";
        discoveryStore.paired(discovered, lan);
        check(discovered.equals(discoveryStore.recognized(tag)), "Tag must identify a unique saved Mac");
        check(java.util.Arrays.equals(first, discoveryStore.recognizedPin(discovered, tag)), "Discovery must keep the saved certificate pin");
        check(discoveryStore.all().get(0).addresses().equals(List.of(lan)), "Unverified discovered address must not be saved");
        check(discoveryStore.recognizedPin(discovered, "oaKjpKWmL4V47Ctn") == null, "Invalid tag must not select a pin");
        discoveryStore.paired(duplicate, vpn);
        check(discoveryStore.recognized(tag) == null && discoveryStore.recognizedPin(discovered, tag) == null,
                "Ambiguous tag must not choose an identity");
        discoveryStore.forget(discovered);
        check(discoveryStore.recognizedPin(discovered, tag) == null, "Forgotten identity cannot reconnect from a stale row");
        System.out.println("Saved Mac identity, migration, persistence and forget checks passed");
    }

    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejected identity"); }
        catch (IllegalArgumentException expected) { }
    }
    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();
        public Map<String, ?> getAll() { return new HashMap<>(values); }
        public Editor edit() {
            return new Editor() {
                final Map<String, Object> updates = new HashMap<>();
                final Set<String> removals = new HashSet<>();
                public Editor putString(String key, String value) { updates.put(key, value); return this; }
                public Editor putStringSet(String key, Set<String> value) { updates.put(key, Set.copyOf(value)); return this; }
                public Editor remove(String key) { removals.add(key); return this; }
                public void apply() { removals.forEach(values::remove); values.putAll(updates); }
            };
        }
    }
}
