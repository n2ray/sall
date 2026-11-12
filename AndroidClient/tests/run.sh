#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/PairingProof.java "$root"/app/src/main/java/io/github/n2ray/sill/StreamWire.java "$root"/app/src/main/java/io/github/n2ray/sill/Hevc.java "$root"/app/src/main/java/io/github/n2ray/sill/FrameGeometry.java "$root"/app/src/main/java/io/github/n2ray/sill/AudioPackets.java "$root"/app/src/main/java/io/github/n2ray/sill/DesktopNavigation.java "$root"/tests/PairingProofTest.java "$root"/tests/StreamWireTest.java "$root"/tests/HevcTest.java "$root"/tests/FrameGeometryTest.java "$root"/tests/AudioPacketsTest.java "$root"/tests/DesktopNavigationTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.PairingProofTest
java -ea -cp "$test_dir" io.github.n2ray.sill.StreamWireTest
java -ea -cp "$test_dir" io.github.n2ray.sill.HevcTest
java -ea -cp "$test_dir" io.github.n2ray.sill.FrameGeometryTest
java -ea -cp "$test_dir" io.github.n2ray.sill.AudioPacketsTest
java -ea -cp "$test_dir" io.github.n2ray.sill.DesktopNavigationTest
