# Isolated overlay device probe

Run only on an unlocked test phone when requested:

```
ANDROID_HOME=<sdk> JAVA_HOME=<jdk> SILL_GRADLE=<gradle> python3 run.py <adb-serial>
```

The runner first backs up and hashes the installed Sill APK, builds a temporary copy under a random separate application ID, and verifies the original APK hash again. It never replaces or reads the original app's data. The temporary app has no INTERNET or CAMERA permission, performs no discovery, does not register the `sill://pair` link, uses a fake in-memory session/socket and synthetic link, and uses its own isolated preferences. It checks external-link confirmation, input gating, cancellation and secret cleanup, an old dialog, an old attempt, rejection/retry, and stop cleanup. The temporary package is uninstalled on exit; if cleanup or verification fails the original backup is retained at the printed path. A disconnect or device shutdown can prevent automatic cleanup.

This is an instrumented MainActivity running in a temporary modified copy: the fake session constructor, no-op discovery implementation (which never obtains Android's NSD service) and forced no-camera scanner are probe-only. It does not validate an actual camera, scanner decoding, system camera permission, a real Mac, TLS, proof exchange, real socket timeouts, transport error callback, screen rotation, PiP, or a genuine OS background transition. The saved-record failure path and asynchronous network callback after a stream reconnect need dedicated injectable transport/store seams and are not exercised by this probe. It leaves the production overlay entry disabled.
