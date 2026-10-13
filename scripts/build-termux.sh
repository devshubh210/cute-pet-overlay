#!/usr/bin/env bash
# Build debug APK in Termux
set -e
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
if [ ! -f "$ANDROID_HOME/platform-tools/adb" ]; then
  echo "SDK not found. Run first: bash scripts/setup-sdk-termux.sh"
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "Installing gradle..."
  pkg install -y gradle openjdk-17
fi
export JAVA_HOME="${JAVA_HOME:-/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk}"
echo "Building with Gradle wrapper (8.7)..."
echo "NOTE: On ARM phones (Termux) the Android SDK's AAPT2 binary is x86_64-only"
echo "and fails with 'Exec format error'. If that happens, build via GitHub Actions"
echo "(Option B in README) instead — it builds on x86_64 Ubuntu and works."
echo ""
bash gradlew assembleDebug --stacktrace
APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "SUCCESS: $APK"
  ls -lh "$APK"
  echo ""
  echo "Install: copy APK out of Termux and tap it, or:"
  echo "  termux-open $APK"
else
  echo "Build failed - APK not found"
  exit 1
fi
