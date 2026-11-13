# Device input-connection check

This test copies the Android project to a temporary directory, adds a non-exported test Activity
and an instrumentation runner, and uses a local listener. It never connects to a Mac or writes
Sill preferences. The script backs up the exact installed Sill APK, verifies the replacement has
the same signer, checks preferences, and restores and verifies the original APK on exit. If restore
fails, it retains the backup path and reports it instead of deleting it.

Installations may interrupt a Sill session. Run only on an authorized debug device, with its original
debug signing key available and no concurrent Sill build or device testing. A process kill, lost
wireless connection, or device shutdown during replacement can prevent automatic restore; keep the
reported backup and reconnect to restore it with `adb install -r` if necessary.

```sh
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk SILL_GRADLE=/path/to/gradle \
  python3 AndroidClient/tests/ime-device/run.py <adb-serial>
```

The smaller `tests/remote-keyboard-connection.sh` test uses framework stand-ins and runs without a
device; the device test uses Android's actual `BaseInputConnection` and `Editable` implementations.
