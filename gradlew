#!/usr/bin/env bash

################################################################################
# Gradle Wrapper Stub for CI/CD builds on GitHub Actions without full binaries #
################################################################################

echo "========================================================="
echo " PDF Utility Tools: Gradle Wrapper Stub Initiated"
echo "========================================================="

if command -v gradle &> /dev/null; then
    echo "System-level Gradle detected, delegating task..."
    gradle "$@"
else
    echo "No standard 'gradle' command found. Attempting to fall back to default path..."
    if [ -x "/usr/bin/gradle" ]; then
        /usr/bin/gradle "$@"
    elif [ -x "/usr/local/bin/gradle" ]; then
        /usr/local/bin/gradle "$@"
    else
        echo "Error: Gradle is not pre-installed or found in PATH."
        echo "Please ensure the GitHub Action has setup-java with Gradle caching or pre-installed gradle."
        exit 1
    fi
fi
