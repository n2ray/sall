# Sall Android client

Sall is an unofficial Android client for the original [Sill](https://github.com/Saffsanity/sill) Mac host. Version 0.1.0-alpha.1 is an early test build. It uses `io.github.n2ray.sall`; earlier `io.github.n2ray.sill` development installations have separate data and require pairing again.

Pair with a QR code or pairing code, connect to a Mac running the original host, and choose a window or desktop. Remote access requires Mac-side Remote Access and a network route such as Tailscale. Audio is experimental and off by default; enable both Mac audio sending and the phone's sound option. Some Mac windows cannot match the phone panel because of minimum size constraints. Capabilities such as multiple-display navigation depend on the installed host version.

## Build

Use JDK 17 or 21, Android SDK platform 35, and Gradle 8.10.2. No Android Studio GUI is required.

```sh
cd AndroidClient
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
sh tests/run.sh
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. A release build needs `SALL_KEYSTORE_PATH` and `SALL_KEYSTORE_PASSWORD` in the environment and the signing key alias `sall`:

```sh
ANDROID_HOME=/path/to/android-sdk gradle assembleRelease
```

Keep the signing keystore and password private, backed up outside Git, and together; losing the key prevents signing future updates to the same application. The local `.sall-signing` directory is ignored by Git. Never include signing credentials in a release or repository.

Real Android device tests live under `tests/`. Read each probe's README before running it: some temporarily replace the installed APK, require an unlocked device or interrupt an active session. The synthetic audio probe has legacy controls that can fail independently of the production player; its report must be inspected rather than treated as a single playback verdict.

See [IOS_PARITY.md](IOS_PARITY.md) for current implementation and verification limits. Sill is licensed under the repository's Apache License 2.0; Sall is not an official Sill release.
