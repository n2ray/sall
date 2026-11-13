# Android group-name grapheme regression

This probe runs synthetic combining-mark, skin-tone, family ZWJ and flag strings through the production group-name normalization on the device. It uses no user preferences, microphone, network connection or Mac data.

```sh
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk SILL_GRADLE=/path/to/gradle \
  python3 AndroidClient/tests/group-device/run.py <adb-serial>
```

The script backs up and verifies the installed Sill APK, temporarily installs an APK with the same signer and a test instrumentation, and restores and verifies the exact original APK on exit. It force-stops Sill; if restoration fails it retains and prints the backup location. Android 29 and the target phone should each be tested separately; results from one version do not establish behavior on another.
