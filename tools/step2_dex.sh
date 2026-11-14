#!/bin/sh
# 步骤 2：javac 编译 + d8 转 dex（在 Alpine Linux 侧执行）
set -e

WORK=/workspace/build-check
OUT=$WORK/apk
JAR=$WORK/android-35/android.jar
XPOSED=$WORK/libs/api-82.jar
SRC=/workspace/cutout-battery-ring/app/src/main/java
D8=$WORK/build-tools/lib/d8.jar

rm -rf "$OUT/classes" "$OUT/dex"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "== javac =="
find "$SRC" -name '*.java' > "$WORK/sources.txt"
echo "$OUT/gen/io/github/eta/cutoutring/R.java" >> "$WORK/sources.txt"
# 输出 class 版本 61（Java 17），保证 d8 能解析
javac --release 17 -nowarn -encoding UTF-8 -d "$OUT/classes" -cp "$JAR:$XPOSED" "@$WORK/sources.txt"
echo "JAVAC_OK"

echo "== d8 =="
CLASSES=$(find "$OUT/classes" -name '*.class' | tr '\n' ' ')
java -cp "$D8" com.android.tools.r8.D8 --min-api 29 --lib "$JAR" --output "$OUT/dex" $CLASSES
echo "D8_OK"
ls -l "$OUT/dex"
