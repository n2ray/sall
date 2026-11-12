#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/PairingProof.java "$root"/app/src/main/java/io/github/n2ray/sill/StreamWire.java "$root"/app/src/main/java/io/github/n2ray/sill/Hevc.java "$root"/app/src/main/java/io/github/n2ray/sill/FrameGeometry.java "$root"/app/src/main/java/io/github/n2ray/sill/PictureZoom.java "$root"/app/src/main/java/io/github/n2ray/sill/PictureGesture.java "$root"/app/src/main/java/io/github/n2ray/sill/AudioPackets.java "$root"/app/src/main/java/io/github/n2ray/sill/DesktopNavigation.java "$root"/tests/PairingProofTest.java "$root"/tests/StreamWireTest.java "$root"/tests/HevcTest.java "$root"/tests/FrameGeometryTest.java "$root"/tests/PictureZoomTest.java "$root"/tests/PictureGestureTest.java "$root"/tests/AudioPacketsTest.java "$root"/tests/DesktopNavigationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.PairingProofTest
java -ea -cp "$test_dir" io.github.n2ray.sill.StreamWireTest
java -ea -cp "$test_dir" io.github.n2ray.sill.HevcTest
java -ea -cp "$test_dir" io.github.n2ray.sill.FrameGeometryTest
java -ea -cp "$test_dir" io.github.n2ray.sill.PictureZoomTest
java -ea -cp "$test_dir" io.github.n2ray.sill.PictureGestureTest
java -ea -cp "$test_dir" io.github.n2ray.sill.AudioPacketsTest
java -ea -cp "$test_dir" io.github.n2ray.sill.DesktopNavigationTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/RemoteTextState.java "$root"/tests/RemoteTextStateTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.RemoteTextStateTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/PointerSync.java "$root"/tests/PointerSyncTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.PointerSyncTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/CursorShapeBlob.java "$root"/tests/CursorShapeBlobTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.CursorShapeBlobTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/ReconnectPolicy.java "$root"/tests/ReconnectPolicyTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.ReconnectPolicyTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/AppIconBlob.java "$root"/tests/AppIconBlobTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.AppIconBlobTest
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/WindowFit.java "$root"/tests/WindowFitTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.WindowFitTest
