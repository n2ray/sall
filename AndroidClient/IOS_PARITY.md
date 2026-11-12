# iOS client parity tracker

Reference: the iOS source in this repository, including `StreamScreen.swift`, `PortraitStreamScreen.swift`, `StreamClient.swift`, `TrackpadView.swift`, `TextComposition.swift`, `HostSettingsPanel.swift`, and their protocol/test helpers. The installed Mac host in the initial test is Sill 0.6.1; features in the current repository may require a newer host. A check here means code and its specific verification exist, not blanket equivalence.

Product direction: follow the iOS client's layout and interaction by default. Earlier Android-specific picture-trackpad, side-control landscape layout, and automatic text-scale adjustment are being replaced by the original behavior.

## Connection and security

- [ ] Paired mutual TLS, certificate pinning, incorrect/expired codes, identity changes and revocation: core path works on the Xiaomi over Tailscale; remaining error cases need end-to-end checks.
- [ ] Discovery and saved Macs: saved records use verified certificate identity, preserve legacy address pins, and group multiple addresses. Bonjour recognition checks the saved key and still verifies the TLS pin; migration and discovery need device verification. Authenticated MacInfo updates and automatic address fallback remain.
- [ ] QR/deep-link pairing: Android accepts validated pairing links, has a camera scanner with explicit identity confirmation, and can try discovered local TLS endpoints for links without addresses. Scanner presentation differs from iOS; end-to-end verification remains. Local discovery currently tries at most eight endpoints without an explicit address.
- [ ] Reconnect and recovery: bounded retry, keyframe recovery and iOS-style automatic desktop selection for a new session with no source are implemented. On the Xiaomi, two returns restored the original window and a third return after a socket disconnect automatically restored the desktop. Chain migration preserving the previous window is not implemented. Verify Wi-Fi/mobile transitions, Mac sleep/restart, revocation, stalled connections and cancellation. Handle host goodbye reasons as iOS does.
- [ ] Host locked/off-console state: ordinary lock and other-user states have separate handling, with a cover until a newly decoded frame is presented. Normal streaming was verified on the Xiaomi; lock/unlock and other-user transitions still require real-device verification.

## Windows and picture

- [ ] Window catalog, app icons, app launching, menu and thumbnail commands exist. The application drawer follows iOS app-name search and cycles windows per application; its landscape version opens at the left over the picture. On 2026-11-12 the user confirmed the drawer stays open through rotation on the Xiaomi. The window strip has a configurable hold time, anchored action menu, per-Mac drag ordering and accessibility actions; those still need device verification. iOS-style portrait presentation, real-time drag animation and full menu state remain.
- [ ] Selected-window fitting: iOS starts with Aa off and fits the complete picture without stretching. On 2026-11-12 the user confirmed Aa changes the picture on the Xiaomi, although App Store showed no visible difference between 1.25 and 1.5. Test repeated source changes, rotation, app minimum sizes and host refusal; some applications cannot fill the picture without cropping.
- [ ] Stage Manager may cause the Mac host to supply a tilted thumbnail. The iOS client displays the host thumbnail; Android follows that behavior. Test window closure, source changes and cache cleanup.
- [ ] Desktop display cycling is implemented only when the host advertises it. The tested Mac 0.6.1 does not; the Android client cannot add the missing host capability.
- [ ] Zoom, pan and gesture ownership: current 1–5× pinch is present. On 2026-11-12 the user confirmed portrait tap and pinch on the Xiaomi; still test the complete finger lifecycle, cancellation, rotation and focal-point preservation against `StreamZoom.swift`.
- [ ] Portrait Larger Picture, soft keyboard accommodation and responsive layouts; on 2026-11-12 the user confirmed the landscape top toolbar and large picture. Larger Picture is connected, but IME height, detailed proportions and interaction still need real-device checks; its layout and drawer presentation are not yet identical to iOS. Android 10 cannot currently measure the IME height.

## Input and local interaction

- [ ] Trackpad single/double click, drag, two-finger scroll and right click, scroll momentum, reverse scrolling, three/four-finger gestures and cancel/rotation behavior; never send a click during a pinch. On 2026-11-12 the user confirmed two-finger scrolling and momentum felt smooth without accidental clicks on the Xiaomi. A persistent reverse-scroll setting and capability-gated Mac gestures are implemented; the new direct picture-input path and multi-touch gestures need real-device checks.
- [ ] IME composition, correction, selection, emoji and dictation: Return/Tab fences, stale callback handling and safe deletion have logic checks; test Chinese input, emoji and dictation on the device. Cross-fence selection editing is conservatively rejected and does not yet match iOS in all cases.
- [ ] Physical keyboard: local-dialog focus gating, function keys and capability-gated repeat are implemented with logic checks; verify real keyboards, shortcuts, older hosts and third-party input methods.
- [ ] Mouse and pointer: shape and basic host position exist; complete source/ownership, out-of-frame visibility, physical mouse buttons and wheel, and no cursor jumps after reconnect.
- [ ] Clipboard: opt-in text sharing and acknowledgement-before-paste are implemented with logic checks; verify slow-network cancellation on a real Mac and complete echo handling, background behavior and precise status.

## Audio, settings and lifecycle

- [ ] Viewport frame rate follows display capability with a 60 fps cap on remote or power-saving connections; verify dynamic changes on hardware. Android VPN route classification may not identify every split-tunnel route.
- [ ] Sound: AAC-ELD decoding is experimental and off by default. Partial PCM writes are retried with cancellation and logic tests; test real playback and implement iOS-quality timing, mute, output-route changes, reconnect and audio/video synchronization.
- [ ] Complete host settings and local preferences, with capability gating, host acknowledgements and failure handling; include scrolling, gestures, long-press duration and Larger Picture. Landscape settings open at the right of the picture and reanchor after rotation; these changes need device verification.
- [ ] Picture in Picture and foreground/background handling: opt-in PiP, pause/resume, timeout and input/viewport/clipboard isolation are implemented and need device verification. Android exits the activity on PiP stop; idle-screen policy and foreground-only clipboard updates also need device lifecycle verification.
- [ ] TalkBack, larger fonts, announcements and actions; connection/error/version/slow-network status and first-run guidance.
- [ ] Foldables and external displays: design for Android's own device capabilities after phone functionality is verified; do not claim iPhone Duo-specific behavior is already supported.

## Verification

Every implementation task needs focused logic tests plus a real-device scenario where hardware, OS, input, video or network behavior matters. Record whether a feature is implemented, verified locally, verified on the Xiaomi, or blocked by the installed host. Run `tests/run.sh`, `assembleDebug` and the Android CI workflow after each batch. Do not put pairing codes, screenshots, typed text or clipboard contents in the repository or logs.
