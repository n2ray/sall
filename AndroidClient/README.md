# Sill Android (development preview)

A separate Android client for the unmodified Sill Mac host. This is an early test build, not a finished remote desktop app.

The current client supports manually addressed pairing with a 12-digit code, pinned mutual TLS, a window list, HEVC video, basic touch and text. The Mac must have Sill running and the screen recording and accessibility permissions enabled. For connections through a VPN, enable Remote Access in Sill and use the Mac's VPN address and configured port (7455 by default). This client does not set up a VPN or open a port on the internet.

Local USB tests can forward a phone port to the Mac's local Sill port with `adb reverse`; this is only a temporary testing route and does not work after unplugging the cable. Pairings are currently stored per entered address and port.

Not implemented: automatic discovery, QR pairing, audio, clipboard, scrolling, a full keyboard or touchpad, external display layout, and connection management for multiple Macs. Video sizing, input mapping and reconnection need further device testing.

Build with JDK 17 or 21, Android SDK platform 35 and Gradle 8.10.2:

```sh
cd AndroidClient
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. `tests/run.sh` runs the protocol vectors and framing checks using Java. Debug builds are signed with the local debug key; differently signed APKs cannot update one another.

The Sill project is licensed under Apache License 2.0; see the repository's LICENSE. Android-specific code is part of this fork and is not an official Sill release.
