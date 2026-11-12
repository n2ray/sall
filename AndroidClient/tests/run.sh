#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
javac -d "$test_dir" "$root"/app/src/main/java/io/github/n2ray/sill/PairingProof.java "$root"/app/src/main/java/io/github/n2ray/sill/StreamWire.java "$root"/app/src/main/java/io/github/n2ray/sill/Hevc.java "$root"/tests/PairingProofTest.java "$root"/tests/StreamWireTest.java "$root"/tests/HevcTest.java
java -ea -cp "$test_dir" io.github.n2ray.sill.PairingProofTest
java -ea -cp "$test_dir" io.github.n2ray.sill.StreamWireTest
java -ea -cp "$test_dir" io.github.n2ray.sill.HevcTest
