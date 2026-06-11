#!/usr/bin/env sh
set -eu

APK="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
  echo "Missing $APK"
  echo "Run: ./gradlew assembleDebug"
  exit 1
fi

adb install -r "$APK"
