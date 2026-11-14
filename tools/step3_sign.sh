#!/bin/sh
# 步骤 3：把 classes.dex 打进 APK 并签名（在 Alpine Linux 侧执行）
set -e

WORK=/workspace/build-check
OUT=$WORK/apk
KS=$WORK/keystore.jks
APKSIGNER=$WORK/build-tools/lib/apksigner.jar

cd "$OUT"

echo "== 打包 =="
python3 - <<'PY'
import shutil, zipfile, os
os.chdir('/workspace/build-check/apk')
shutil.copy('base.apk', 'unsigned.apk')
with zipfile.ZipFile('unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write('dex/classes.dex', 'classes.dex')
print('unsigned.apk 条目:', zipfile.ZipFile('unsigned.apk').namelist())
PY

echo "== 生成签名密钥（仅首次） =="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias cutoutring -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=CutoutRing,O=Eta,C=CN" >/dev/null 2>&1
  echo "已生成 $KS"
else
  echo "复用已有 $KS"
fi

echo "== apksigner sign =="
java -jar "$APKSIGNER" sign \
  --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/app-release.apk" "$OUT/unsigned.apk"

echo "== 校验签名 =="
java -jar "$APKSIGNER" verify --print-certs "$OUT/app-release.apk" | head -12

ls -l "$OUT/app-release.apk"
