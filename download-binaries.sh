#!/bin/bash
# 下载 OpenList Android 二进制文件并放入 jniLibs 目录
#
# 核心原理：
# - Android 10+ 的 SELinux W^X 策略禁止从 filesDir 执行二进制
# - 解决方案：将二进制重命名为 libopenlist.so，放入 jniLibs/<abi>/
# - Android 安装时会提取到 nativeLibraryDir（具有 exec_type 标签，允许执行）
# - 需要配合 build.gradle 中的 useLegacyPackaging = true

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNI_DIR="$SCRIPT_DIR/app/src/main/jniLibs"

echo "=== 下载 OpenList Android 二进制文件 ==="

BASE_URL="https://github.com/weijia/OpenList/releases/download/beta"

# 创建 jniLibs 目录结构
mkdir -p "$JNI_DIR/arm64-v8a"
mkdir -p "$JNI_DIR/armeabi-v7a"
mkdir -p "$JNI_DIR/x86_64"
mkdir -p "$JNI_DIR/x86"

# arm64-v8a (大多数现代手机)
echo "[1/4] 下载 arm64-v8a 版本..."
if [ ! -f "$JNI_DIR/arm64-v8a/libopenlist.so" ]; then
    curl -L -o /tmp/openlist-arm64.tar.gz "$BASE_URL/openlist-android-arm64.tar.gz"
    cd /tmp && tar -xzf openlist-arm64.tar.gz
    mv /tmp/openlist "$JNI_DIR/arm64-v8a/libopenlist.so"
    rm /tmp/openlist-arm64.tar.gz
    echo "  ✓ arm64-v8a 完成"
else
    echo "  ✓ arm64-v8a 已存在，跳过"
fi

# armeabi-v7a (旧设备)
echo "[2/4] 下载 armeabi-v7a 版本..."
if [ ! -f "$JNI_DIR/armeabi-v7a/libopenlist.so" ]; then
    curl -L -o /tmp/openlist-arm.tar.gz "$BASE_URL/openlist-android-arm.tar.gz"
    cd /tmp && tar -xzf openlist-arm.tar.gz
    mv /tmp/openlist "$JNI_DIR/armeabi-v7a/libopenlist.so"
    rm /tmp/openlist-arm.tar.gz
    echo "  ✓ armeabi-v7a 完成"
else
    echo "  ✓ armeabi-v7a 已存在，跳过"
fi

# x86_64 (模拟器)
echo "[3/4] 下载 x86_64 版本..."
if [ ! -f "$JNI_DIR/x86_64/libopenlist.so" ]; then
    curl -L -o /tmp/openlist-amd64.tar.gz "$BASE_URL/openlist-android-amd64.tar.gz"
    cd /tmp && tar -xzf openlist-amd64.tar.gz
    mv /tmp/openlist "$JNI_DIR/x86_64/libopenlist.so"
    rm /tmp/openlist-amd64.tar.gz
    echo "  ✓ x86_64 完成"
else
    echo "  ✓ x86_64 已存在，跳过"
fi

# x86 (旧模拟器)
echo "[4/4] 下载 x86 版本..."
if [ ! -f "$JNI_DIR/x86/libopenlist.so" ]; then
    curl -L -o /tmp/openlist-386.tar.gz "$BASE_URL/openlist-android-386.tar.gz"
    cd /tmp && tar -xzf openlist-386.tar.gz
    mv /tmp/openlist "$JNI_DIR/x86/libopenlist.so"
    rm /tmp/openlist-386.tar.gz
    echo "  ✓ x86 完成"
else
    echo "  ✓ x86 已存在，跳过"
fi

echo ""
echo "=== 下载完成 ==="
echo ""
echo "文件结构："
find "$JNI_DIR" -name "libopenlist.so" -exec ls -lh {} \;
echo ""
echo "总计大小："
du -sh "$JNI_DIR"
