# 项目交接文档（HANDOFF）

> 给下一个会话/接手者的完整上下文。**先读这份，再看代码。**

## 1. 这是什么

挖孔电量环：LSPosed 模块，在屏幕挖孔周围画一圈电量进度环，跑在 SystemUI 进程内自绘，
不新开窗口、不占通知栏。

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/afetera/CutoutBatteryRing （public，MIT） |
| 包名 | `io.github.eta.cutoutring` |
| 作用域 | `com.android.systemui` |
| 当前版本 | 1.22（versionCode 23） |
| 最新提交 | 见 `git log -1`（1.22 修复插电绿被配置重载重置） |
| 作者署名 | afetera `<zhanglingyu000@126.com>` |

## 2. 开发/测试设备

- Android 16 / LineageOS（用 SUSFS 隐藏了 LOS 特征）
- 屏幕 1240×2772，560 dpi，**挖孔 center=(620, 74) r=35px**（与 `dumpsys display` 的 cutoutSpec 一致）
- Root：KernelSU（`su` 可用）；LSPosed 已启用本模块；装了 Thanox
- 这台设备上 APK 直接 `pm install -r`，改代码后可重启 SystemUI 验证

## 3. 代码地图

| 文件 | 职责 |
|---|---|
| `ModuleEntry` | LSPosed 入口，按包名分发 |
| `RingInstaller` | hook 状态栏窗口（4 个候选类）+ 控制广播（reload / restart / ping）+ 宿主注入 |
| `RingOverlayView` | 状态栏里的绘制 View：挖孔几何、电量广播、呼吸、插电旋转、按应用隐藏 |
| `RingRenderer` | **共用绘制核心**（渐变、深色调暗、呼吸 alpha、充电色混合），预览与真机共用 |
| `RingConfig` / `Prefs` | 配置快照 / 键与默认值 |
| `MonetColors` / `MonetPalette` | 莫奈调色板读取与 4 套渐变方案 |
| `CutoutPathUtil` | 反射 `DisplayCutout#getCutoutPath()` |
| `SystemProbe` | 前台包名（`ActivityTaskManager.getFocusedRootTaskInfo`） |
| `MainActivity` + `Page` | 三栏宿主 + 页面基类 |
| `HomePage` / `PersonalizePage` / `SettingsPage` | 首页 / 个性化 / 设置 |
| `BottomNavView` / `StepperView` / `ColorPickerDialog` / `UiKit` / `Binders` | 自绘控件与工具 |
| `AppPickerActivity` | 按应用隐藏的选择器 |
| `RingPreviewView` | 设置页预览（含"播放插电动画"演示） |

## 4. 关键技术决策（含踩过的坑，别重复踩）

1. **挂载方式**：把自绘 View 作为**状态栏窗口的子 View**注入。
   Android 11+ 非系统窗口新开 overlay 会被降级，画不进挖孔区；状态栏窗口本身有
   `layoutInDisplayCutoutMode=always` 的许可。
2. **挖孔几何**：优先反射 `getCutoutPath()` 取屏幕坐标路径再 `computeBounds`；
   失败时退回 `getBoundingRects()` + 设置里的 X/Y 微调。
3. **配置通道**：**不要用 `XSharedPreferences`**——跨 SELinux 域读 App 私有目录，
   实测读到的永远是默认值。现方案：App 把整份配置打包成广播推给 SystemUI，
   SystemUI 存进自己的私有 prefs 再刷新（已验证 SystemUI 重启后能自行恢复）。
4. **前台应用**：`ActivityTaskManager.getService().getFocusedRootTaskInfo()`（@hide，
   SystemUI 持有 `REAL_GET_TASKS`），只在"隐藏列表非空"时才 1 秒轮询。
5. **重启 SystemUI**：自定义签名级权限保护的广播 + `Process.killProcess(myPid())`，
   persistent 进程会被系统拉起。已实测：普通应用身份发送无效，权限保护有效。
6. **包可见性**：manifest 必须声明 `<queries>`（MAIN/LAUNCHER），否则
   Android 11+ 下应用选择器只能看到系统应用（这个是真实踩过的 bug）。
7. **首页模块状态**：`ACTION_PING` / `ACTION_PONG` 通道，SystemUI 回传运行时解析到的
   挖孔坐标与隐藏应用数，是"真实探测"而非猜测。
8. **配置重载会新建 `RingConfig`**：`reloadConfig()` 每 30 秒跑一次兜底轮询，
   `RingConfig.load()` 返回全新对象，`chargeBlend`（充电色混合比例）、`pulseAlpha`（呼吸亮度）、
   `animStartDeg`（动画角度）这些**运行时字段会回到默认值**。所以 reload 后必须从旧快照继承，
   并按当前 `charging` 校正 `chargeBlend`——否则会出现「插电变绿 → 约 30 秒后又恢复原色」。
   这类字段不属于 prefs，别想着从 SharedPreferences 里读回来。
9. **电池充电状态看 `EXTRA_STATUS`，不是 `EXTRA_PLUGGED`**：`cmd battery unplug` 只改供电标志、
   不改 `status`，用它测不了模块的拔电分支；要复现拔电请用 `cmd battery set status 3`，
   插电用 `cmd battery set status 2`，测完 `cmd battery reset` 还原。

## 5. 已放弃的方案（别再试）

- **截图/录屏时隐藏环**：Android 16 的系统截图不走 SystemUI 的 `TakeScreenshotService`；
  给状态栏窗口设 `FLAG_SECURE`（调用成功）后截图里依然可见；`screencap` 抓 SurfaceFlinger
  所有层更不受影响；`MediaProjectionManager$Callback` 是**抽象类**不是接口，无法动态代理。
- **`XSharedPreferences`**：见上文第 3 条。
- **Material Components (MD3) 组件库**：手工 aapt2 链无法合并 AAR 资源。
  实测：material 1.12 的 656 个资源能 `aapt2 compile`，但与我们的资源一起 `link` 直接报
  `style/Animation.AppCompat.Dialog not found`、`attr/dialogPreferredPadding not found`
  —— 需要整棵依赖树（约 30 个 AAR）按顺序合并，且本地无 Gradle / Android SDK。
- **充电功率显示**（1.19–1.20 试验，1.21 已移除）：功率数据本身能稳定读到
  （`BatteryManager` 电流 + 广播电压，30 秒采样），但无论做成内外双环还是叠加弧，
  真机观感都不理想，用户明确要求不要。没有新的视觉方案前别再加回来。

## 6. 构建

**路线一（推荐）：GitHub Actions**
推送到 `main` → `.github/workflows/build.yml` 用 JDK17 + Gradle 构建 debug APK，
产物在 Actions → 对应运行 → Artifacts。已多次验证 `success`。

**路线二：设备端手工链（无需 PC/SDK）**
位置 `/data/local/tmp/eta/build-check/`（= Linux 环境 `/workspace/build-check/`）：

| 脚本 | 运行环境 | 说明 |
|---|---|---|
| `step1_aapt2.sh` | **android** | aapt2（Termux arm64 版，需 `LD_LIBRARY_PATH`）编译资源并 link，生成 `apk/base.apk` 与 R.java |
| `step2_dex.sh` | **linux (alpine)** | javac（JDK 25，`--release 17`）+ d8 → `classes.dex` |
| `step3_sign.sh` | **linux (alpine)** | 打包 dex + apksigner 签名（keystore 在 `build-check/keystore.jks`） |

依赖：`android-35/android.jar`、`libs/api-82.jar`、`build-tools/lib/{d8.jar,apksigner.jar}`、
`termux-root/.../bin/aapt2`。版本号硬编码在 `step1_aapt2.sh` 里（改版本要 `sed`）。
装包：`pm install -r .../app-release.apk`，并复制到 `/sdcard/Download/`。

**注意**：`environment` 参数很容易搞混——`javac/d8/apksigner/aapt2 编译` 混用会失败，
按上表选环境。

## 7. 版本演进

| 版本 | 内容 |
|---|---|
| 1.0 | 首个版本：挖孔周围电量环 |
| 1.6–1.7 | 实时预览、颜色选择器、重启按钮；配置改为广播推送 |
| 1.8 | 按应用隐藏；数值微调改为 `− 输入框 +` 三件套 |
| 1.9 | 莫奈渐变（4 套）、深色调暗、低电量呼吸、横屏隐藏、应用列表修复 |
| 1.10–1.13 | 重构为三栏 UI（首页/个性化/设置）、底部自绘导航、模块状态探测 |
| 1.14 | 插入充电器时旋转两圈（ease-out），速度可调 |
| 1.15–1.17 | 首页改为"播放插电动画"按钮，可重复播放 |
| 1.18 | 插电/拔电颜色 600ms 平滑过渡（渐变两端也参与混合） |
| 1.19 | （已废弃）充电功率内环试验 |
| 1.20 | App 布局整理：颜色一行四选、位置微调折叠、更新日志折叠、底部留白；功率改为叠加显示（仅试验） |
| 1.21 | 移除充电功率显示，回到纯粹的电量环；保留 1.20 的布局整理 |
| 1.22 | 修复插电变绿后又被 30 秒配置重载重置回原色；无 sticky 广播时补读充电状态 |

## 8. 实测数据（可复现，作为基线）

- 挖孔：`center=(620.0,74.0) radius=35.0`
- 配置重载：32–803 µs（本地 prefs）
- 莫奈渐变：逐 20° 采样，色阶 Δ3–4
- 深色调暗：`(158,206,219)` → `(103,134,142)`（35%）
- 呼吸：6 帧亮度 71.7→91.0→120.7→113.3→76.7→83.7
- 插电旋转：帧间转角 208°→194°→114°→76°→44°，累计约 720°
- 颜色过渡：`(140,210,216)`→`(109,193,151)`→`(65,159,87)`→`(76,175,80)`
- 按应用隐藏：桌面环带蓝色像素 18–19，设置应用内 0
- 应用列表：修复后 78 个（含第三方）
- 无 wakelock、无 alarm、无网络

## 9. 已知限制

- 挖孔非圆形（药丸/双孔）时半径按 `min(宽,高)/2` 会偏小 → 用"手动半径"补偿
- 换 ROM 可能 4 个宿主候选都不命中 → 看 `logcat | grep CutoutRing`
- 设置页拿不到 `DisplayCutout`（Android 15+ 不给普通应用窗口）→ 预览用 12dp 示意半径
- 满电（100%）时插电看不到旋转（整圈旋转对称）
- 仅在一台设备（Android 16 / LOS）实测过

## 10. 可以继续做的事

1. **真 MD3 重设计**：需要 Gradle + AndroidX 环境（CI 或 Android Studio），
   本地手工链做不到；改完每轮要下载 CI artifact 装机验证
2. 手绘 M3 设计语言（可行，保持现有链）：卡片 24dp 圆角、pill 按钮、
   NavigationBar 的 pill 指示器、type scale
3. QS 磁贴一键开关
4. 内环数据源可选：适配器侧（usb sysfs）vs 电池侧
5. 满电插电时的替代反馈（亮度闪两下 / 从 0 扫满）
6. 配置导入导出

## 11. 快速上手命令

```bash
# 当前安装版本
dumpsys package io.github.eta.cutoutring | grep versionName

# 模块运行日志（关键三行：宿命中 / 挂载 / 挖孔坐标）
logcat -d | grep CutoutRing


# 推送配置（示例：改充电动画速度）
am broadcast --user 0 -a io.github.eta.cutoutring.action.RELOAD \
  -p com.android.systemui --ef charge_anim_speed 1.5

# 重启 SystemUI（等价于 App 里的按钮）
am broadcast --user 0 -a io.github.eta.cutoutring.action.RESTART_SYSTEMUI \
  -p com.android.systemui
```

注意：`am broadcast` 用 **root** 身份才能通过接收器的权限校验（普通应用会被拒）。
