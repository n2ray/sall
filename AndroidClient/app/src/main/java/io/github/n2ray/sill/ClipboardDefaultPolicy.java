package io.github.n2ray.sill;

/** Never overwrite an explicit setting; unknown history retains the old disabled default. */
final class ClipboardDefaultPolicy {
    interface Store {
        boolean contains();
        boolean value();
        boolean hasExistingData();
        boolean commit(boolean enabled);
    }
    private ClipboardDefaultPolicy() { }

    static boolean initial(boolean explicit, boolean stored, boolean existingData, long firstInstall, long lastUpdate, long now) {
        if (explicit) return stored;
        return !existingData && firstInstall > 0 && firstInstall == lastUpdate && now > 0 && firstInstall <= now;
    }

    static boolean initialize(Store store, long firstInstall, long lastUpdate, long now) {
        try {
            if (store.contains()) return store.value();
            boolean enabled = initial(false, false, store.hasExistingData(), firstInstall, lastUpdate, now);
            boolean persisted;
            try { persisted = store.commit(enabled); } catch (RuntimeException ignored) { persisted = false; }
            if (persisted) return enabled;
            // Android can mutate its in-memory map even when its disk commit fails.
            try { store.commit(false); } catch (RuntimeException ignored) { }
        } catch (RuntimeException ignored) { }
        return false;
    }
}
