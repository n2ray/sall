# iOS visual reference inventory

Reference is the SwiftUI source in this repository. Dimensions are points (Android uses dp as the starting equivalent), not device pixels. This is a specification, not a claim that Android matches it. SF Symbols, system font rendering, continuous corners and accessibility sizes need device comparison.

## Shared palette and panels

`iOSClient/StreamScreen.swift` `Palette`: bar `#16181C`, control `#23262C`, open control `#2C3550`, accent `#9BB7FF`, text `#EEF0F3`, bar label `#C4C9D1`, muted `#A4ABB5`, panel `#1F2126`, trackpad `#101215`, thumbnail body `#26282D`, thumbnail title `#34373D`, icon fallback `#3A3D44`. Floating panels use 20-point corners, 14-point padding, a 10%-white border and a black 65% shadow. Main picture background is black.

## Connection and pairing

`ContentView.swift` `ConnectScreen`, `ConnectionList.swift`, `AddMacCard.swift`, `CodeScanner.swift`, `PairingOverlay.swift`: a centered, maximum 380-point column with a 17-point semibold title, 13-point muted status, tile/list switch and default Mac tiles. Tiles use 92-point previews, 12-point corners and a name/route footer. Add Mac replaces the list with a card: scanner first, or a typed-code path; fields are 48 high, errors inline, primary button accent. A short landscape screen places scanner and explanation side by side. The footer has links and version. Pairing over an existing stream has its own dark overlay. Scanner permissions, errors and connection state each have distinct presentation. Grouping, previews and their deletion, scanner permissions and accessible layout require verification.

## Stream and controls

`StreamScreen.swift` `BarMetrics`, `TopBar`, `BarButton`; `PortraitStreamScreen.swift`; `PhonePortraitLayout.swift`: landscape compact top bar is 78 high, 14 horizontal padding and 12 gaps; buttons 64×58, 22-point icon over an 11-point label, open buttons use controlOpen/accent. Apps, window strip, conditional Menus, Aa, Keyboard, Desktop, Settings appear in that order. Compact thumbnails are 92×58 with 14 spacing; regular layout uses 104×66 and 16 spacing. The picture uses aspect fit, an 8-point margin and 12-point corners.

Phone portrait puts the 16:10 picture first, then a 50-point row of five icon/label controls (14-point side margins and 8-point gaps), an 80×50 thumbnail strip, a 44-point six-key row and the trackpad. Larger Picture removes the strip under its source and size conditions. Portrait and landscape also differ in panel anchors; rotation must retain state.

`StreamScreen.swift` `WindowStrip` and window controls: thumbnails aspect-fill, with a subtle border, selected accent halo and app badge protruding at lower left. Long-press offers the three colored Mac window actions. `TextScaleControl` uses Aa and a press-and-slide ruler, with an unavailable state. `MacMenuButton.swift` and `MacMenuElements.swift` define conditional Mac menus and their enabled/checked states. `ClipboardChip.swift` defines the small bordered clipboard capsule. Loading, lock, slow-network and disconnect states are distinct, not interchangeable.

## Apps, trackpad and settings

`StreamScreen.swift` `AppDrawer`: floating panel, 48-point search, 13-point muted section headers, 50-point open-app rows, 46-point other-app rows, 32-point icons. Selected row is control, other rows transparent. Landscape panel opens from the left with a 58%-black dim; portrait anchors below the controls (above them in Larger Picture). Locked and other-user states have different contents.

`TrackpadView.swift`: trackpad background, 22-point corners, 8%-white border, 18-point dot grid and bottom-centered 13-point muted instruction. Gestures and pointer ownership remain governed by the original interaction rules.

`HostSettingsPanel.swift`: one floating panel with fixed Mac/status header and Done, scrollable grouped settings and a fixed Disconnect button. Groups use control background, 12-point corners, 44-point minimum rows and inset separators; explanatory text is 13-point muted and warnings orange. Includes quality, frame rate, resolution, encoding, virtual display, sound, connection, device preferences, tour and version as supported. Settings opens on the right in landscape without dimming the picture; pending, unavailable, rejected and older-host states remain visible.

## Guidance and overlays

`TourOverlay.swift`, `TourPolicy.swift`: 58%-black mask with highlighted targets, 360-point card (480 short landscape, 560 accessibility), 20-point corners, 14-point padding and arrow. Skip is plain accent text; Next is accent with black text, at least 88×44; six-point progress dots indicate the current step. Long text, VoiceOver and rotation have separate placement rules. The first screen, remaining steps and large fonts require visual and accessibility comparison.

## Verification boundary

Compare real iOS and Android captures at matching dimensions, sources and states, in both orientations and with large text. Preserve platform input and security semantics. Do not persist real Mac screen previews solely for visual comparison. The full Android parity and test status is tracked in `IOS_PARITY.md`.
