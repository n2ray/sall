#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionDiagnostics.java "$root"/tests/ConnectionDiagnosticsTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ConnectionDiagnosticsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionPreviewPolicy.java "$root"/app/src/main/java/io/github/n2ray/sall/MemoryPreviews.java "$root"/tests/MemoryPreviewsTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.MemoryPreviewsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/SessionMacInfoCache.java "$root"/tests/SessionMacInfoCacheTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.SessionMacInfoCacheTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HomeAskState.java "$root"/tests/HomeAskStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HomeAskStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/RecognitionTag.java "$root"/app/src/main/java/io/github/n2ray/sall/HomeDiscoveryMetadata.java "$root"/app/src/main/java/io/github/n2ray/sall/LocalOpenPolicy.java "$root"/tests/LocalOpenPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.LocalOpenPolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PairingProof.java "$root"/app/src/main/java/io/github/n2ray/sall/StreamWire.java "$root"/app/src/main/java/io/github/n2ray/sall/Hevc.java "$root"/app/src/main/java/io/github/n2ray/sall/FrameGeometry.java "$root"/app/src/main/java/io/github/n2ray/sall/PictureZoom.java "$root"/app/src/main/java/io/github/n2ray/sall/MultiFingerGesture.java "$root"/app/src/main/java/io/github/n2ray/sall/AudioPackets.java "$root"/app/src/main/java/io/github/n2ray/sall/DesktopNavigation.java "$root"/tests/PairingProofTest.java "$root"/tests/StreamWireTest.java "$root"/tests/HevcTest.java "$root"/tests/FrameGeometryTest.java "$root"/tests/PictureZoomTest.java "$root"/tests/AudioPacketsTest.java "$root"/tests/DesktopNavigationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PairingProofTest
java -ea -cp "$test_dir" io.github.n2ray.sall.StreamWireTest
java -ea -cp "$test_dir" io.github.n2ray.sall.HevcTest
java -ea -cp "$test_dir" io.github.n2ray.sall.FrameGeometryTest
java -ea -cp "$test_dir" io.github.n2ray.sall.PictureZoomTest
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioPacketsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioPcmWriter.java "$root"/tests/AudioPcmWriterTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioPcmWriterTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioMutePolicy.java "$root"/tests/AudioMutePolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioMutePolicyTest
java -ea -cp "$test_dir" io.github.n2ray.sall.DesktopNavigationTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/RemoteTextState.java "$root"/tests/RemoteTextStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.RemoteTextStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PointerSync.java "$root"/tests/PointerSyncTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PointerSyncTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/CursorShapeBlob.java "$root"/tests/CursorShapeBlobTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.CursorShapeBlobTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ReconnectPolicy.java "$root"/tests/ReconnectPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ReconnectPolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/GoodbyePolicy.java "$root"/tests/GoodbyePolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.GoodbyePolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HostSettingsOptions.java "$root"/tests/HostSettingsOptionsTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HostSettingsOptionsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AppIconBlob.java "$root"/tests/AppIconBlobTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AppIconBlobTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardReceipt.java "$root"/tests/ClipboardReceiptTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ClipboardReceiptTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardChipState.java "$root"/tests/ClipboardChipStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ClipboardChipStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionPreviewPolicy.java "$root"/tests/ConnectionPreviewPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ConnectionPreviewPolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionPreviewPolicy.java "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionPreviewStore.java "$root"/tests/ConnectionPreviewStoreTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ConnectionPreviewStoreTest
PYTHONDONTWRITEBYTECODE=1 python3 "$root/tests/preview-device/test_sequence.py"
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardChangeTracker.java "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardChipState.java "$root"/tests/ClipboardChangeTrackerTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ClipboardChangeTrackerTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardPastePolicy.java "$root"/tests/ClipboardPastePolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ClipboardPastePolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ClipboardDefaultPolicy.java "$root"/tests/ClipboardDefaultPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ClipboardDefaultPolicyTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/TourPolicy.java "$root"/app/src/main/java/io/github/n2ray/sall/TourContent.java "$root"/app/src/main/java/io/github/n2ray/sall/TourPlacement.java "$root"/tests/TourPolicyTest.java "$root"/tests/TourContentTest.java "$root"/tests/TourPlacementTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.TourPolicyTest
java -ea -cp "$test_dir" io.github.n2ray.sall.TourContentTest
java -ea -cp "$test_dir" io.github.n2ray.sall.TourPlacementTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/InputGeneration.java "$root"/tests/InputGenerationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.InputGenerationTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HardwareKeyGate.java "$root"/tests/HardwareKeyGateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HardwareKeyGateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HardwareKeyFlow.java "$root"/tests/HardwareKeyFlowTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HardwareKeyFlowTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ScrollMomentum.java "$root"/tests/ScrollMomentumTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ScrollMomentumTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/MultiFingerGesture.java "$root"/tests/MultiFingerGestureTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.MultiFingerGestureTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AppDrawerModel.java "$root"/tests/AppDrawerModelTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AppDrawerModelTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionGroups.java "$root"/tests/ConnectionGroupsTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ConnectionGroupsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HostLockState.java "$root"/tests/HostLockStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HostLockStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/SessionIdlePolicy.java "$root"/tests/SessionIdlePolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.SessionIdlePolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PhoneStreamLayout.java "$root"/tests/PhoneStreamLayoutTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PhoneStreamLayoutTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PictureStroke.java "$root"/tests/PictureStrokeTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PictureStrokeTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ViewportFps.java "$root"/tests/ViewportFpsTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ViewportFpsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PairLink.java "$root"/tests/PairLinkTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PairLinkTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/KeyframeRecovery.java "$root"/tests/KeyframeRecoveryTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.KeyframeRecoveryTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/WindowHold.java "$root"/tests/WindowHoldTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.WindowHoldTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/MacMenuRow.java "$root"/tests/MacMenuRowTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.MacMenuRowTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/MacMenuState.java "$root"/tests/MacMenuStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.MacMenuStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/WindowOrder.java "$root"/tests/WindowOrderTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.WindowOrderTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/InitialSourcePolicy.java "$root"/tests/InitialSourcePolicyTest.java
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioRecoveryPolicy.java "$root"/tests/AudioRecoveryPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioRecoveryPolicyTest
java -ea -cp "$test_dir" io.github.n2ray.sall.InitialSourcePolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/VideoDecoderPolicy.java "$root"/tests/VideoDecoderPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.VideoDecoderPolicyTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ReconnectSourcePolicy.java "$root"/tests/ReconnectSourcePolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ReconnectSourcePolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/TextScale.java "$root"/tests/TextScaleTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.TextScaleTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PhysicalMouse.java "$root"/tests/PhysicalMouseTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PhysicalMouseTest
javac -d "$test_dir" "$root"/tests/fixtures/android/content/SharedPreferences.java "$root"/app/src/main/java/io/github/n2ray/sall/PairLink.java "$root"/app/src/main/java/io/github/n2ray/sall/SavedMacs.java "$root"/app/src/main/java/io/github/n2ray/sall/SavedMacStore.java "$root"/app/src/main/java/io/github/n2ray/sall/SignedMacInfo.java "$root"/app/src/main/java/io/github/n2ray/sall/RecognitionTag.java "$root"/tests/SavedMacsTest.java
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/ConnectionRouteLabel.java "$root"/tests/ConnectionRouteLabelTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.ConnectionRouteLabelTest
java -ea -cp "$test_dir" io.github.n2ray.sall.SavedMacsTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/RecognitionTag.java "$root"/tests/RecognitionTagTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.RecognitionTagTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/HomeDiscoveryMetadata.java "$root"/tests/HomeDiscoveryMetadataTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.HomeDiscoveryMetadataTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/WindowAutoFit.java "$root"/tests/WindowAutoFitTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.WindowAutoFitTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/DiscoveryEpoch.java "$root"/tests/DiscoveryEpochTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.DiscoveryEpochTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PipPolicy.java "$root"/tests/PipPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PipPolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PairLink.java "$root"/app/src/main/java/io/github/n2ray/sall/SavedMacs.java "$root"/app/src/main/java/io/github/n2ray/sall/SignedMacInfo.java "$root"/tests/SignedMacInfoTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.SignedMacInfoTest "$root"/tests/fixtures/mac-info/cryptokit.properties
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/RemoteDial.java "$root"/tests/RemoteDialTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.RemoteDialTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/FirstWindowList.java "$root"/tests/FirstWindowListTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.FirstWindowListTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioPlayout.java "$root"/tests/AudioPlayoutTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioPlayoutTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioDecodeMapping.java "$root"/tests/AudioDecodeMappingTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioDecodeMappingTest

javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/AudioOutputTimeline.java "$root"/tests/AudioOutputTimelineTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.AudioOutputTimelineTest
sh "$root/tests/audio-schedule-join.sh"
sh "$root/tests/remote-keyboard-connection.sh"
javac -cp "$test_dir" -d "$test_dir" "$root"/tests/RemoteReconnectTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.RemoteReconnectTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/SessionAuthorization.java "$root"/tests/SessionAuthorizationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.SessionAuthorizationTest
javac -cp "$test_dir" -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/PairingAuthorization.java "$root"/tests/PairingAuthorizationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.PairingAuthorizationTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/OverlayPairingAuthorization.java "$root"/tests/OverlayPairingAuthorizationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.OverlayPairingAuthorizationTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/OverlayPairingDispatch.java "$root"/tests/OverlayPairingDispatchTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.OverlayPairingDispatchTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sall/QrPairingAttempt.java "$root"/tests/QrPairingAttemptTest.java
java -ea -cp "$test_dir" io.github.n2ray.sall.QrPairingAttemptTest
