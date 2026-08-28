#!/usr/bin/env bash

################################################################################
# Gradle Wrapper Self-Bootstrapper & Launcher for Linux / Android / Termux / CI
################################################################################

set -e

GRADLE_VERSION="8.2"
GRADLE_DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_DIR="$GRADLE_USER_HOME/wrapper/dists/gradle-${GRADLE_VERSION}-bin"
GRADLE_BIN="$GRADLE_DIR/gradle-${GRADLE_VERSION}/bin/gradle"

# 1. Check if standard gradle command is available in PATH
if command -v gradle &> /dev/null; then
    exec gradle "$@"
fi

# 2. Check if already auto-downloaded
if [ -x "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
fi

# 3. Check common system paths
if [ -x "/usr/bin/gradle" ]; then
    exec /usr/bin/gradle "$@"
elif [ -x "/usr/local/bin/gradle" ]; then
    exec /usr/local/bin/gradle "$@"
fi

# 4. Auto-download and unpack Gradle
echo "========================================================="
echo " PDF Utility Tools: Downloading Gradle ${GRADLE_VERSION}..."
echo "========================================================="

mkdir -p "$GRADLE_DIR"
ZIP_PATH="$GRADLE_DIR/gradle-${GRADLE_VERSION}-bin.zip"

if command -v curl &> /dev/null; then
    curl -sSL "$GRADLE_DIST_URL" -o "$ZIP_PATH"
elif command -v wget &> /dev/null; then
    wget -q "$GRADLE_DIST_URL" -O "$ZIP_PATH"
else
    echo "Error: Neither curl nor wget found. Please install curl or wget or install gradle manually:"
    echo "  apt install -y gradle curl"
    exit 1
fi

if command -v unzip &> /dev/null; then
    unzip -q -o "$ZIP_PATH" -d "$GRADLE_DIR"
    rm -f "$ZIP_PATH"
    chmod +x "$GRADLE_BIN"
    echo "Gradle ${GRADLE_VERSION} ready!"
    exec "$GRADLE_BIN" "$@"
else
    echo "Error: unzip command not found. Please install unzip: apt install -y unzip"
    exit 1
fi
