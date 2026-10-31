#!/usr/bin/env bash
# lightmeter 构建脚本（Windows / Git Bash）
#
# 为什么需要它：Gradle 需要 JAVA_HOME 和 ANDROID_HOME 两个环境变量，
# 缺任一个都会报 "SDK location not found"，而错误信息里不会告诉你去设哪个。
#
# 用法：
#   ./build.sh debug
#   ./build.sh release
#   ./build.sh test
#   ./build.sh clean

set -euo pipefail

JDK="/c/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"
SDK="/c/Users/Admin/AppData/Local/Android/Sdk"

if [ ! -d "$JDK" ]; then
  echo "缺少 JDK 17。安装：winget install EclipseAdoptium.Temurin.17.JDK" >&2
  exit 1
fi
if [ ! -d "$SDK/platforms/android-34" ]; then
  echo "缺少 Android SDK 34。用 sdkmanager 安装：platforms;android-34 build-tools;34.0.0" >&2
  exit 1
fi

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"

cd "$(dirname "$0")"

case "${1:-debug}" in
  debug)   ./gradlew.bat assembleDebug --no-daemon ;;
  release) ./gradlew.bat assembleRelease --no-daemon ;;
  test)    ./gradlew.bat testDebugUnitTest --no-daemon ;;
  clean)   ./gradlew.bat clean --no-daemon ;;
  *) echo "用法: $0 [debug|release|test|clean]" >&2; exit 2 ;;
esac
