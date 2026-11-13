#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
src="$root/app/src/main/java/io/github/n2ray/sill"
javac -d "$classes" "$src/AacEldConfig.java" "$root/tests/AacEldConfigTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AacEldConfigTest
javac -d "$classes" "$src/AudioPackets.java" "$src/AudioPlayout.java" "$src/AudioDecodeMapping.java" "$src/AudioOutputTimeline.java" "$src/AudioScheduleJoin.java" "$root/tests/AudioScheduleJoinTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AudioScheduleJoinTest
javac -d "$classes" "$src/AudioPresentationClock.java" "$root/tests/AudioPresentationClockTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AudioPresentationClockTest
javac -cp "$classes" -d "$classes" "$src/AudioRuntime.java" "$root/tests/AudioRuntimeTest.java"
java -ea -cp "$classes" io.github.n2ray.sill.AudioRuntimeTest
