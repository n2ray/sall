package io.github.n2ray.sill;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Uses a synthetic, isolated preference file; never opens MainActivity's preferences. */
public final class PrefsProbe extends Instrumentation {
    private String phase;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); phase = arguments.getString("phase", ""); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            SharedPreferences prefs = context.getSharedPreferences("sill_origin_probe", Context.MODE_PRIVATE);
            byte[] pin = new byte[32]; pin[0] = 73;
            SavedMacs.Endpoint endpoint = new SavedMacs.Endpoint("192.0.2.1", 7455);
            if (phase.equals("write")) {
                if (!prefs.edit().clear().commit()) throw new AssertionError("clear failed");
                new SavedMacStore(prefs).paired(SavedMacs.Identity.migrated(pin), endpoint);
                if (!prefs.edit().putBoolean("probe_committed", true).commit()) throw new AssertionError("commit failed");
            } else if (phase.equals("read")) {
                if (!prefs.getBoolean("probe_committed", false)) throw new AssertionError("write not persisted");
                java.util.List<SavedMacs.Mac> records = new SavedMacStore(prefs).all();
                if (records.size() != 1 || !records.get(0).addresses().equals(java.util.List.of(endpoint))
                        || !java.security.MessageDigest.isEqual(records.get(0).identity().pin(), pin))
                    throw new AssertionError("serialized identity or address failed to reload");
                if (!prefs.edit().putBoolean("probe_finished", true).commit()) throw new AssertionError("flush failed");
                context.deleteSharedPreferences("sill_origin_probe");
            } else throw new AssertionError("invalid phase");
            result.putString("result", "PASS: synthetic SharedPreferences " + phase);
            finish(android.app.Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error.getClass().getSimpleName());
            finish(android.app.Activity.RESULT_CANCELED, result);
        }
    }
}
