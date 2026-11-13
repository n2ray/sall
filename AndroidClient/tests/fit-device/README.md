# Isolated window-fit probe

This probe builds an independent, randomly named debug package. It removes INTERNET and CAMERA permissions, disables discovery and the external Sill URI handler, and uses synthetic geometry plus a memory-backed fake connection. It never updates or reads data from the installed Sill application. The runner verifies the original APK hash before and after the probe and uninstalls only the random probe package.

Run `python3 run.py <adb-serial> --build-only` to build and check the manifest, permissions, package identity, and original APK hash without installing or displaying anything. Omit `--build-only` to run the on-device instrumentation; it opens a separate temporary test Activity and briefly takes the foreground.

The isolated Activity also creates a synthetic saved Mac and encrypted preview, confirms removal in the actual dialog, and verifies the record, encryption key and ciphertext are gone. It requires a recent device-credential authentication on Android 15+. The tests exercise automatic viewport computation and encoded output, including a matching sample followed by changed dimensions and a two-axis minimum, same-window snapshot reuse after a synthetic layout reset, stale sample rejection, rejection of another window or connection, manual Aa priority, cancellation retry, pause retention, and PiP and foreground size guards. Inputs are injected directly into the Activity: this does not verify actual host list ordering, Android rotation, full PiP transitions, or a real Mac window. `ANDROID_HOME`, `JAVA_HOME`, and a working Gradle must be configured; `SILL_GRADLE` can point to the Gradle executable.

Use `--skip-preview` to run only the fit and reconnect checks when recent device authentication is unavailable. This explicitly omits the encrypted-preview and removal checks; the default still requires and runs them.

Set `SILL_UI_CAPTURE` to save a rendering of the synthetic settings switch. The probe checks its displayed state and that a click invokes the switch action exactly once.

The probe also feeds the Activity a deliberately released HEVC decoder and checks that the session remains connected, the failed decoder is discarded, and a software candidate and keyframe request are selected when available. It then creates the actual software decoder and queues a locally generated HEVC Main keyframe. It does not prove successful decoding on a particular third-party tablet.
