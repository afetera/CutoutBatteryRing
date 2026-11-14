# 挖孔电量环

![Build APK](https://github.com/afetera/CutoutBatteryRing/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-blue)

在屏幕挖孔周围画一圈电量进度环。LSPosed 模块，直接跑在 SystemUI 进程里，不新开窗口、不占用通知栏。

## 功能

- **电量环**：顺时针一圈表示电量，充电时变绿（可关）
- **四种配色**：跟随系统莫奈（单色）、莫奈渐变（4 套强调色方案）、自定义单色、自定义渐变
- **深色模式调暗**：夜间把环压暗，避免刺眼（强度可调）
- **低电量呼吸**：低于设定阈值时呼吸闪烁，周期可调，充电中自动停止
- **显示范围**：可以在指定应用里隐藏，也可以横屏时隐藏
- **精确微调**：每个参数都有 `−` `输入框` `+` 和滑杆三种方式
- **状态可见**：首页直接显示模块有没有生效、挖孔的实际坐标

## 安装

1. 安装 APK（[Releases](../../releases) 或 Actions 里的构建产物）
2. 在 **LSPosed** 中启用模块，作用域勾选 **系统界面 / SystemUI**
3. 打开 App，点首页的「重启 SystemUI」

首页显示 **「已注入 SystemUI，运行中」** 并带出挖孔坐标，就说明生效了。

## 界面

| 页面 | 内容 |
|---|---|
| 首页 | 电量环预览、模块状态、更新日志、一键重启 SystemUI |
| 个性化 | 外观尺寸（粗细、间距、起始角度）与配色 |
| 设置 | 显示范围、夜间与提醒、位置微调 |

## 兼容性

挖孔位置全部通过系统 `DisplayCutout` API 实时获取，**没有硬编码坐标**，换机型、换分辨率、旋转、折叠屏展开都会自动重新对齐。

唯一的不确定性在 SystemUI 的窗口类名。模块内置了 4 个 AOSP 类名作为候选，覆盖 Android 12–16；大多数基于 AOSP 的 ROM（含 MIUI / ColorOS / OriginOS / One UI）会保留这些类名。

如果换了 ROM 后环不显示，抓一下日志发出来：

```bash
adb logcat | grep CutoutRing
```

正常应该能看到三行，分别对应「命中宿主类」「挂载成功」「挖孔坐标」：

```
hooked host: ...
overlay attached to ... hostSize=...
cutout center=(...) radius=...
```

## 构建

- **自动**：推送到 `main` 后 GitHub Actions 会构建 debug APK，去 Actions → 对应运行 → Artifacts 下载
- **本地**：用 Android Studio 打开本目录，直接 Build
- **设备上**：没有电脑和 SDK 也能在 arm64 安卓机上出包，见 [tools/README-build.md](tools/README-build.md)

## 已知限制

- 普通应用窗口拿不到挖孔数据（Android 15+ 限制），所以设置页的预览用约 12dp 的示意半径；真正的位置由 SystemUI 侧按真机挖孔对齐
- 如果 ROM 开启了「隐藏挖孔」，环也会被一起遮住
- 目前只在 Android 16 / LineageOS 上实测通过

## 许可

[MIT](LICENSE)
