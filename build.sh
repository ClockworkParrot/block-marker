#!/bin/bash
# 离线快速构建：直接使用本地 Mindustry.jar 编译，无需网络与 Gradle。
# 产物: build/libs/BlockMarkerDesktop.jar
# 多平台标准构建请使用 Gradle（build.gradle 来自官方模板）: gradle jar
set -e
cd "$(dirname "$0")"

JAR="${MINDUSTRY_JAR:-/home/clockworkparrot/文档/.MDT/Mindustry.jar}"
if [ ! -f "$JAR" ]; then
    echo "未找到 Mindustry.jar（$JAR），可用 MINDUSTRY_JAR=路径 指定" >&2
    exit 1
fi

rm -rf build/classes build/libs
mkdir -p build/classes build/libs

find src -name "*.java" > build/sources.txt
javac --release 17 -encoding UTF-8 -Xlint:-options -cp "$JAR" -d build/classes @build/sources.txt

cp mod.hjson build/classes/
jar --create --file build/libs/BlockMarkerDesktop.jar -C build/classes .
echo "构建完成: build/libs/BlockMarkerDesktop.jar"
