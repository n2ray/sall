#!/bin/sh
set -eu
serial="${1:?Usage: ANDROID_HOME=... JAVA_HOME=... sh run.sh <adb-serial>}"
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
sdk="${ANDROID_HOME:?ANDROID_HOME is required}"
java="${JAVA_HOME:?JAVA_HOME is required}/bin"
tmp=$(mktemp -d "${TMPDIR:-/tmp}/sall-video-probe.XXXXXX")
remote="/data/local/tmp/sall-video-probe-$$.dex"
cleanup() { "$sdk/platform-tools/adb" -s "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true; rm -rf "$tmp"; }
trap cleanup EXIT HUP INT TERM
"$java/javac" --release 17 -cp "$sdk/platforms/android-35/android.jar" -d "$tmp" "$root/app/src/main/java/io/github/n2ray/sall/VideoSizeLimits.java" "$root/tests/video-device/VideoCodecProbe.java"
"$java/jar" cf "$tmp/probe.jar" -C "$tmp" io
"$sdk/build-tools/35.0.0/d8" --min-api 29 --output "$tmp" "$tmp/probe.jar"
"$sdk/platform-tools/adb" -s "$serial" push "$tmp/classes.dex" "$remote" >/dev/null
"$sdk/platform-tools/adb" -s "$serial" shell "CLASSPATH=$remote app_process /system/bin io.github.n2ray.sall.VideoCodecProbe"
