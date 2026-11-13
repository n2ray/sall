#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
fixtures="$root/tests/fixtures/ime/android"
src="$root/app/src/main/java/io/github/n2ray/sall"
javac -d "$classes" "$fixtures"/content/*.java "$fixtures"/text/*.java "$fixtures"/view/*.java "$fixtures"/view/inputmethod/*.java \
    "$src/InputGeneration.java" "$src/RemoteTextState.java" "$src/RemoteKeyboard.java" "$root/tests/RemoteKeyboardConnectionTest.java"
java -ea -cp "$classes" io.github.n2ray.sall.RemoteKeyboardConnectionTest
