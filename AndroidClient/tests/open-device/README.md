# Isolated local open-door probe

Builds a random-package Sill client with its own Android identity and preferences, and no `sill://` link handler. It never replaces or reads the installed Sill application's data. It waits at `READY` before installation so the person can prepare the Mac; `go` installs the probe and `cancel` stops without installing it. It requires exactly one locally discovered open TLS door and a Wi-Fi/Ethernet Network reported by Android NSD. `--inspect` only checks discovery and whether an unconnected socket can bind; it does not require changing the Mac setting. The probe connects, checks that a frame was presented and an unpaired warning is visible, verifies no Mac was saved, and disconnects. It uninstalls its own random package and verifies the original APK hash.

The Mac's Require pairing setting must be turned off by its owner only for this test and restored immediately afterward. This exposes the Mac to other local devices during that interval. It does not verify reconnects, TLS identity changes, or real input. No remote screen or pairing secret is read by the runner.

```sh
ANDROID_HOME=<sdk> JAVA_HOME=<jdk> SILL_GRADLE=<gradle> python3 run.py <adb-serial>
```

On the tested Xiaomi with Tailscale active, Android returned EPERM for explicit Wi-Fi socket binding; with Tailscale disconnected, binding succeeded. The app fails closed and shows a VPN guidance message rather than silently using another route.
