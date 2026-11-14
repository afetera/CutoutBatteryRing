#!/system/bin/sh
# 步骤 1：用 aapt2（Termux arm64 构建）编译资源并链接出 APK 骨架 + R.java
set -e

A=/data/local/tmp/eta/build-check/termux-root/data/data/com.termux/files/usr
AAPT2="$A/bin/aapt2"
export LD_LIBRARY_PATH="$A/lib"

JAR=/data/local/tmp/eta/build-check/android-35/android.jar
SRC=/data/local/tmp/eta/cutout-battery-ring/app/src/main
OUT=/data/local/tmp/eta/build-check/apk

rm -rf "$OUT"
mkdir -p "$OUT/gen"

# 源 manifest 交给 AGP 时用 namespace 注入包名，这里手工补上 package 属性（不改动源文件）
sed 's|<manifest |<manifest package="io.github.eta.cutoutring" |' \
    "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
grep -m1 "<manifest" "$OUT/AndroidManifest.xml"

echo "== aapt2 compile =="
"$AAPT2" compile --dir "$SRC/res" -o "$OUT/res.zip"
echo "COMPILE_OK"

echo "== aapt2 link =="
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$JAR" \
  --manifest "$OUT/AndroidManifest.xml" \
  --java "$OUT/gen" \
  -A "$SRC/assets" \
  --min-sdk-version 29 \
  --target-sdk-version 34 \
  --version-code 14 \
  --version-name 1.13 \
  "$OUT/res.zip"
echo "LINK_OK"

echo "== 产物 =="
find "$OUT" -type f | sort
