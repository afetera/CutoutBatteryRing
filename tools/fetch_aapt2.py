#!/usr/bin/env python3
"""从 Termux 仓库下载 aapt2 及其依赖（aarch64 的 Android 原生二进制）。"""
import os
import re
import io
import sys
import tarfile
import urllib.request

WORK = "/workspace/build-check"
BASE = "https://packages.termux.dev/apt/termux-main/"
DEB_DIR = os.path.join(WORK, "debs")
ROOT = os.path.join(WORK, "termux-root")

os.makedirs(DEB_DIR, exist_ok=True)
os.makedirs(ROOT, exist_ok=True)

index = open("/tmp/Packages", encoding="utf-8", errors="ignore").read()
entries = {}
for block in index.split("\n\n"):
    if not block.strip():
        continue
    fields = {}
    for line in block.splitlines():
        if ": " in line:
            key, value = line.split(": ", 1)
            fields[key] = value
    if "Package" in fields:
        entries[fields["Package"]] = fields

resolved = []
seen = set()


def add(name):
    name = name.strip()
    if not name or name in seen:
        return
    seen.add(name)
    entry = entries.get(name)
    if entry is None:
        print("  [skip] 索引中无此包: %s" % name)
        return
    for dep in entry.get("Depends", "").split(","):
        dep = re.sub(r"\s*\(.*?\)", "", dep).strip()
        if dep:
            add(dep)
    resolved.append(entry)


add("aapt2")

print("需要 %d 个包" % len(resolved))
failures = []
for entry in resolved:
    filename = entry["Filename"]
    local = os.path.join(DEB_DIR, os.path.basename(filename))
    if not os.path.exists(local):
        url = BASE + filename
        try:
            print("下载 %-24s (%s bytes)" % (entry["Package"], entry.get("Size", "?")))
            urllib.request.urlretrieve(url, local)
        except Exception as exc:  # noqa: BLE001
            print("  下载失败: %s" % exc)
            failures.append(entry["Package"])
            continue
    # 解包 .deb（ar 归档）
    try:
        with open(local, "rb") as handle:
            if handle.read(8) != b"!<arch>\n":
                raise ValueError("不是 ar 归档")
            while True:
                header = handle.read(60)
                if len(header) < 60:
                    break
                raw_name = header[0:16].decode(errors="ignore").strip()
                # GNU ar 的 BSD 长名扩展："#1/<len>"，真实名字跟在头部之后
                extra = 0
                if raw_name.startswith("#1/"):
                    extra = int(raw_name[3:])
                    real = handle.read(extra)
                    name = real.decode(errors="ignore").strip()
                    size = int(header[48:58].decode().strip()) - extra
                else:
                    name = raw_name.rstrip("/")
                    size = int(header[48:58].decode().strip())
                payload = handle.read(size)
                if size % 2:
                    handle.read(1)
                if name.startswith("data.tar"):
                    ext = name.rsplit(".", 1)[-1]
                    mode = {"xz": "r:xz", "gz": "r:gz", "bz2": "r:bz2"}.get(ext)
                    if mode is None:
                        raise ValueError("未支持的压缩格式: %s" % ext)
                    with tarfile.open(fileobj=io.BytesIO(payload), mode=mode) as tar:
                        tar.extractall(ROOT, filter="data")
    except Exception as exc:  # noqa: BLE001
        print("  解包失败 %s: %s" % (entry["Package"], exc))
        failures.append(entry["Package"])

print("失败包:", failures if failures else "无")
for path, dirs, files in os.walk(os.path.join(ROOT, "data/data/com.termux/files/usr/bin")):
    for name in sorted(files):
        print("bin:", os.path.join(path, name))
    break
libdir = os.path.join(ROOT, "data/data/com.termux/files/usr/lib")
if os.path.isdir(libdir):
    print("lib 文件数:", len(os.listdir(libdir)))
sys.exit(1 if failures else 0)
