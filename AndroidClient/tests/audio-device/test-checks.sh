#!/bin/sh
set -eu
here=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac -d "$classes" "$here/AudioProbeChecks.java" "$here/AudioProbeChecksTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AudioProbeChecksTest
javac -d "$classes" "$here/EsdsDecoderSpecificInfo.java" "$here/EsdsDecoderSpecificInfoTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.EsdsDecoderSpecificInfoTest
javac -d "$classes" "$here/../../app/src/main/java/io/github/n2ray/sill/AudioPresentationClock.java" \
  "$here/AudioProbeClock.java" "$here/AudioProbeClockTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AudioProbeClockTest
