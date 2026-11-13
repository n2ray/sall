#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
javac -d "$classes" "$root/app/src/main/java/io/github/n2ray/sall/AudioPlayout.java" "$root/app/src/main/java/io/github/n2ray/sall/AudioOutputTimeline.java" "$root/tests/AudioOutputTimelineTest.java"
java -ea -cp "$classes" io.github.n2ray.sall.AudioOutputTimelineTest
