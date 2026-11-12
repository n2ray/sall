package android.content;

import java.util.Map;
import java.util.Set;

/** Minimal JVM contract for exercising SavedMacStore with an in-memory preference store. */
public interface SharedPreferences {
    Map<String, ?> getAll();
    Editor edit();
    interface Editor {
        Editor putString(String key, String value);
        Editor putStringSet(String key, Set<String> values);
        Editor remove(String key);
        void apply();
    }
}
