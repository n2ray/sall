# Controlled live external-link probe

This script provides a temporary masked input for a controlled same-Mac live check. With a USB-connected phone:

```
ANDROID_HOME=<sdk> JAVA_HOME=<jdk> SILL_GRADLE=<gradle> python3 run.py <adb-usb-serial> --seconds 300
```

The script holds the same device/package lock used by the preview probe, backs up the installed single APK, checks its hash, builds a temporary copy, checks the same package and exact signer, then replaces it only after verifying the installed APK has not changed. The production overlay checks that the link's fingerprint equals the fingerprint of the *current* TLS stream before opening the confirmation. The temporary source adds a masked input for testing. Other Mac links are rejected and cleared. No production source is modified.

After installation, the person opens Sill and connects the intended Mac. The temporary build shows “粘贴链接（测试）”; the person pastes a fresh pairing link into its masked input and explicitly confirms the target. No code reads the clipboard: pasting is an explicit system input action. The input is cleared on submit, cancel, stream-session revocation, activity stop and destruction; a temporary loss of window focus alone does not guarantee clearing. It is excluded from saved view state and autofill, and requests no keyboard learning. Android URI and string objects can retain copies in memory until garbage collection, so immediate memory erasure cannot be guaranteed. The runner does not read or print the link, fingerprints, secrets, clipboard, logs, or screen. A successful test may update the existing saved identity and the Mac's device trust record; restoring the APK does not undo those authorized pairing effects.

On the normal timer, Ctrl-C, SIGTERM, or a caught failure, the runner attempts to reinstall and hash-verify the original APK. It prints the private backup path and exact restoration command before replacement. A disconnected phone, computer shutdown or SIGKILL cannot be handled by the process; keep that printed path and run the command after reconnecting, then verify the restored APK. The backup and temporary files are retained if restoration or verification fails. The script does not relaunch Sill after restoration. The device/package lock is keyed by the supplied adb serial, so use the same USB serial for all concurrent probes.
