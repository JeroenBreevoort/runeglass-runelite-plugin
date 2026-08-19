#!/usr/bin/env bash
# Pins the Gradle daemon to a JDK it supports.
#
# Gradle 8.10 (which the wrapper pins, matching the RuneLite plugin template) cannot run on
# JDK 24+ — it fails with "Unsupported class file major version". This writes a gitignored
# gradle.properties pointing the daemon at a supported JDK. The compile target stays Java 11.
set -euo pipefail

cd "$(dirname "$0")/.."

for candidate in \
  /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  "${JAVA_21_HOME:-}"
do
  if [ -n "$candidate" ] && [ -x "$candidate/bin/javac" ]; then
    printf 'org.gradle.java.home=%s\n' "$candidate" > gradle.properties
    echo "Pinned Gradle daemon to: $candidate"
    exit 0
  fi
done

echo "No JDK 21 found. Install one with:" >&2
echo "  brew install openjdk@21" >&2
exit 1
