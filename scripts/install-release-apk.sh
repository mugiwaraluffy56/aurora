#!/usr/bin/env sh
set -eu

APK="app/build/outputs/apk/release/app-release-unsigned.apk"

if [ ! -f "$APK" ]; then
  echo "Missing $APK"
  echo "Run: ./gradlew assembleRelease"
  exit 1
fi

adb install -r "$APK"
