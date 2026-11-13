# SharedPreferences device regression

This probe writes a synthetic identity to a dedicated `sill_origin_probe` preferences file, force-stops the app, and reads it in a new process. It checks actual Android XML serialization and removes the temporary preference file. It does not read the user's saved Macs or the `MainActivity` preferences.

Run on an authorized device after building the normal debug app:

```sh
ANDROID_HOME=/path/to/android-sdk JAVA_HOME=/path/to/jdk \
SILL_GRADLE=/path/to/gradle python3 AndroidClient/tests/prefs-device/run.py <adb-serial>
```

The script temporarily installs a same-signed debug APK, stops any active Sill session, and restores the exact installed APK afterward. If restoration fails it retains the original APK in its printed temporary location.
