#!/usr/bin/env bash
# One-time Android SDK setup in Termux (~1GB download)
set -e
PREFIX_SDK="$HOME/android-sdk"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
TMPD="${TMPDIR:-$PREFIX/tmp}"
mkdir -p "$TMPD"
mkdir -p "$PREFIX_SDK/cmdline-tools"
cd "$TMPD"
if [ ! -f commandlinetools.zip ]; then
  echo "Downloading cmdline-tools..."
  curl -L -o commandlinetools.zip "$CMDLINE_URL"
fi
rm -rf "$PREFIX_SDK/cmdline-tools/latest"
mkdir -p "$PREFIX_SDK/cmdline-tools/latest"
unzip -q -o commandlinetools.zip -d "$PREFIX_SDK/cmdline-tools/latest_tmp"
mv "$PREFIX_SDK/cmdline-tools/latest_tmp/cmdline-tools/"* "$PREFIX_SDK/cmdline-tools/latest/"
rm -rf "$PREFIX_SDK/cmdline-tools/latest_tmp" commandlinetools.zip
export ANDROID_HOME="$PREFIX_SDK"
export ANDROID_SDK_ROOT="$PREFIX_SDK"
export PATH="$PREFIX_SDK/cmdline-tools/latest/bin:$PREFIX_SDK/platform-tools:$PATH"
yes | sdkmanager --licenses || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo ""
echo "SDK ready at $PREFIX_SDK"
echo "Add to ~/.bashrc:"
echo "  export ANDROID_HOME=\$HOME/android-sdk"
echo "  export ANDROID_SDK_ROOT=\$HOME/android-sdk"
echo "  export PATH=\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
