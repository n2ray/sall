# Synthetic preview device probe

This uses a run-unique synthetic identity, Keystore alias and private cache/tombstone directories. It never reads the user's previews, preferences, clipboard, screen or credentials, and never locks the device. It tests AES-GCM storage with synthetic bytes across forced process restarts, foreground-policy rejection, forget, and cleanup. A securely unlocked Android 15+ device and recent strong authentication are required. The probe first creates its isolated key, checks whether it is authorized, and reports `authentication_required` if not. With the explicit `--authenticate` option, it opens Android's system biometric/device-credential prompt once and retries the same key; a cancelled or unavailable prompt is reported as a skip. It does not alter the key's security parameters or recreate the key for retry. This does **not** establish real screen-lock or crash/power-loss security.

The script backs up the installed APK, builds a same-signature temporary instrumented APK in an isolated copy, verifies its signer, replaces the app, and restores and verifies the original APK. It force-stops Sill during the probe. Cleanup is attempted even if the claim response is interrupted; a separate random nonce must match the ownership marker before any deletion. A successful cleanup deletes the synthetic Keystore key, encrypted file, tombstone, private directories and ownership marker. If cleanup or APK restoration fails, the script preserves its temporary directory and reports its location for recovery.

```sh
JAVA_HOME=/path/to/jdk ANDROID_HOME=/path/to/sdk GRADLE_USER_HOME=/path/to/gradle-cache \
  SILL_GRADLE=/path/to/gradle python3 AndroidClient/tests/preview-device/run.py <adb-serial>
```

Add `--authenticate` only when the person is present to complete the system prompt. No password, PIN, fingerprint or biometric data is supplied to or logged by the probe.
