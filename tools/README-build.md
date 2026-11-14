# 在设备上直接构建（无需 PC / Android SDK）

本目录脚本用于**在这台 arm64 Android 设备上**把模块源码编译成可安装的签名 APK。
官方 Android SDK 的 aapt2 只有 x86_64 版本，arm64 设备跑不了，因此这里用
Termux 仓库的 aarch64 原生 aapt2（Android 二进制，可在本机直接运行），
配合 build-tools 里的纯 Java 工具（d8 / apksigner）与 Linux 侧的 JDK 完成构建。

## 依赖来源

| 组件 | 来源 | 说明 |
|---|---|---|
| aapt2 | Termux main 仓库 `aapt2` 包（aarch64） | Android 原生二进制，需 `LD_LIBRARY_PATH` 指向解包出的 Termux lib 目录 |
| android.jar | Google `platform-35_r02.zip` | 编译期 API 签名 |
| d8.jar / apksigner.jar | Google `build-tools_r35.0.1_linux.zip` 的 `lib/` | 纯 Java，用 Alpine 的 JDK 运行 |
| Xposed API | `https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar` | `compileOnly`，不打进 APK |

拉取脚本：`fetch_aapt2.py`（按 Packages 索引递归解析依赖并解包 .deb）。

## 步骤

```sh
# 步骤 1：在 Android 宿主侧编译资源 + 链接出 APK 骨架和 R.java
sh step1_aapt2.sh

# 步骤 2：在 Linux（Alpine）侧编译 Java 并转 dex
sh step2_dex.sh

# 步骤 3：把 classes.dex 打进 APK 并签名
sh step3_sign.sh
```

产物：`apk/app-release.apk`。

## 说明

- `step1_aapt2.sh` 会生成一份带 `package` 属性的临时 manifest（源 manifest 交给 AGP 时
  由 namespace 注入，aapt2 单独使用则必须显式提供）。
- `step2_dex.sh` 用 `javac --release 17`，让 class 文件版本保持 61，便于 d8 解析。
- 签名密钥是脚本首次运行时用 `keytool` 现场生成的 `keystore.jks`
  （口令都是 `android`，仅供本地测试；正式分发请换成自己的密钥）。
- 这套流程同样适用于其它纯 Java 的 LSPosed 模块。
