# iOS client parity tracker

Reference: the iOS source in this repository, including `StreamScreen.swift`, `PortraitStreamScreen.swift`, `StreamClient.swift`, `TrackpadView.swift`, `TextComposition.swift`, `HostSettingsPanel.swift`, and their protocol/test helpers. The installed Mac host in the initial test is Sill 0.6.1; features in the current repository may require a newer host. A check here means code and its specific verification exist, not blanket equivalence.

User choices that intentionally differ from iOS: the picture behaves as a relative trackpad in both orientations; landscape has controls at the sides; preserve the whole window instead of cropping when a Mac app cannot fit.

## Connection and security

- [ ] Paired mutual TLS, certificate pinning, incorrect/expired codes, identity changes and revocation: core path works on the Xiaomi over Tailscale; remaining error cases need end-to-end checks.
- [ ] Discovery and saved Macs: basic local discovery and address/port records exist; add stable Mac identity, names, multiple addresses, route recovery, IPv6, and service-loss handling.
- [ ] QR/deep-link pairing with an explicit identity confirmation; iOS has it, Android does not.
- [ ] Reconnect and recovery: bounded retry exists; verify real Wi-Fi/mobile transitions, Mac sleep/restart, revocation, stalled connections, source continuity and explicit cancellation. Handle host goodbye reasons and keyframe recovery as iOS does.
- [ ] Host locked/off-console state: mirror iOS's distinct ordinary lock and another-user behavior, clear unsafe stale frames and wait for a new keyframe where appropriate.

## Windows and picture

- [ ] Window catalog, app icons, app launching, menu and thumbnail commands exist; add the iOS searchable application drawer, grouping, drag-to-reorder, configurable hold time, full menu state and accessible actions.
- [ ] Selected-window fitting: preserve aspect ratio and full content; automatic fitting is implemented and one constrained Mail window was verified by host logs. Test repeated source changes, rotation, app minimum sizes and host refusal.
- [ ] Stage Manager may cause the Mac host to supply a tilted thumbnail. A selected window gets a locally captured stream preview; background previews may remain skewed or stale. Test window closure, source changes and cache cleanup.
- [ ] Desktop display cycling is implemented only when the host advertises it. The tested Mac 0.6.1 does not; the Android client cannot add the missing host capability.
- [ ] Zoom, pan and gesture ownership: current 1–5× pinch is present; test the complete finger lifecycle, cancellation, rotation and focal-point preservation against `StreamZoom.swift`.
- [ ] Portrait Larger Picture, soft keyboard accommodation and responsive layouts; retain the user's landscape controls and trackpad choices.

## Input and local interaction

- [ ] Trackpad single/double click, drag, two-finger scroll and right click, scroll momentum, reverse scrolling, three/four-finger gestures and cancel/rotation behavior; never send a click during a pinch. Momentum and a persistent reverse-scroll setting are implemented with logic checks; three-finger strokes currently suppress accidental clicks but do not yet send Mac gestures. Verify on the Xiaomi and gate host gestures on advertised capability.
- [ ] IME composition, correction, selection, emoji and dictation: basic Android suffix correction exists; port/check iOS composition fences and stale callback handling with real Chinese input.
- [ ] Physical keyboard: local-dialog focus gating and stale IME callback protection are implemented with logic checks; verify with a real keyboard, then complete modifiers, repeat, function keys, shortcuts and older host compatibility.
- [ ] Mouse and pointer: shape and basic host position exist; complete source/ownership, out-of-frame visibility, physical mouse buttons and wheel, and no cursor jumps after reconnect.
- [ ] Clipboard: opt-in text sharing and acknowledgement-before-paste are implemented with logic checks; verify slow-network cancellation on a real Mac and complete echo handling, background behavior and precise status.

## Audio, settings and lifecycle

- [ ] Sound: AAC-ELD decoding is experimental and off by default. Test real playback and implement iOS-quality timing, mute, output-route changes, reconnect and audio/video synchronization.
- [ ] Complete host settings and local preferences, with capability gating, host acknowledgements and failure handling; include scrolling, gestures, long-press duration and Larger Picture.
- [ ] Picture in Picture, foreground/background handling, idle screen policy, and prevention of background clipboard updates.
- [ ] TalkBack, larger fonts, announcements and actions; connection/error/version/slow-network status and first-run guidance.
- [ ] Foldables and external displays: design for Android's own device capabilities after phone functionality is verified; do not claim iPhone Duo-specific behavior is already supported.

## Verification

Every implementation task needs focused logic tests plus a real-device scenario where hardware, OS, input, video or network behavior matters. Record whether a feature is implemented, verified locally, verified on the Xiaomi, or blocked by the installed host. Run `tests/run.sh`, `assembleDebug` and the Android CI workflow after each batch. Do not put pairing codes, screenshots, typed text or clipboard contents in the repository or logs.
