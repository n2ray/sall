# Sill Android (development preview)

A separate Android client for the unmodified Sill Mac host. This is an early test build, not a finished remote desktop app.

The client supports pairing with a 12-digit code, pinned mutual TLS, saved addresses, local network discovery, HEVC video, window previews and switching, and a portrait remote-control layout. It includes a touchpad, modifier keys, Android keyboard input, app launching, Mac menus and host settings. Text clipboard sharing is opt-in; audio playback is experimental and disabled by default. The Mac must have Sill running and the screen recording and accessibility permissions enabled. For connections through a VPN, enable Remote Access in Sill and use the Mac's VPN address and configured port (7455 by default). This client does not set up a VPN or open a port on the internet.

The screen layout and picture gestures are being aligned with the iOS client. Aa controls whether the Mac resizes a selected window, and starts off like iOS. Some Mac apps cannot fit without blank space because they impose a minimum window size. On a host that reports multiple desktop displays, tapping Desktop while the desktop is active steps through them. Sill for Mac 0.6.1 does not report the multi-display protocol and cannot switch displays through this client.

Window cards use host thumbnails and app icons, like the iOS client. With macOS Stage Manager, the host may send a skewed picture of a background window; an unmodified client cannot recover pixels that the host did not capture.

Local USB tests can forward a phone port to the Mac's local Sill port with `adb reverse`; this is only a temporary testing route and does not work after unplugging the cable. Pairings are currently stored per entered address and port.

This is not yet equivalent to the iOS client. QR pairing, picture-in-picture, and a dedicated external-display layout are not implemented. An established connection retries transient network failures a limited number of times, without replaying input; this still needs real-network testing. Saved pairings are associated with an address and port, so a Mac reached at another address must currently be paired again. Advanced keyboard composition, gestures, audio quality and latency, rotation, and device compatibility need testing on real Android hardware. Local discovery is only a convenience: a pairing code and the pinned host certificate are still required.

Build with JDK 17 or 21, Android SDK platform 35 and Gradle 8.10.2:

```sh
cd AndroidClient
ANDROID_HOME=/path/to/android-sdk gradle assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. `tests/run.sh` runs protocol, geometry and audio-packet checks using Java. Debug builds are signed with the local debug key; differently signed APKs cannot update one another.

The Sill project is licensed under Apache License 2.0; see the repository's LICENSE. Android-specific code is part of this fork and is not an official Sill release.
