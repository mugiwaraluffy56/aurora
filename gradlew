#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.14.4"
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_HOME="$BASE_DIR/.gradle/bootstrap/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
DIST_ZIP="$BASE_DIR/.gradle/bootstrap/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$BASE_DIR/.gradle/bootstrap"
  if [ ! -f "$DIST_ZIP" ]; then
    curl -L "$DIST_URL" -o "$DIST_ZIP"
  fi
  unzip -q "$DIST_ZIP" -d "$BASE_DIR/.gradle/bootstrap"
fi

exec "$GRADLE_BIN" "$@"
