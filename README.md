# 挖孔电量环（Cutout Battery Ring）

LSPosed 模块：在屏幕挖孔周围绘制一圈电量进度环。运行在 SystemUI 进程内，以挖孔为圆心自绘，
不新开窗口、不占用通知栏位置。

当前版本：1.13

## App 界面结构

底部三栏导航（自绘，无 AndroidX 依赖）：

| 页 | 内容 |
|---|---|
| **首页** | 电量环实时预览、模块状态（向 SystemUI 探测真实注入状态与挖孔坐标）、更新日志、快速重启 SystemUI |
| **个性化** | 预览环 + 外观（启用、底轨、粗细、间距、起始角度）+ 颜色（四种取色模式、方案选择、警示色、充电色） |
| **设置** | 显示范围（横屏隐藏、按应用隐藏）、夜间与提醒（调暗强度、呼吸周期）、位置微调、重启入口 |

首页的模块状态是**真实探测**的：App 发 `ACTION_PING`，SystemUI 回 `ACTION_PONG` 并带上
它解析到的挖孔几何与当前隐藏应用数，所以首页显示的是运行中的真值。

另支持 `am start ... --ei page N`（0/1/2）直接打开指定页。

## 功能

- **电量环**：顺时针满圈 = 100%，充电中变绿（可关）
- **四种取色模式**
  - 跟随系统莫奈（单色，取 `system_accent1_200`）
  - **莫奈渐变**：4 套渐变方案，设置页直接预览每个方案
    （刻意用"浅色调 → 深色调"组合：同色调的不同强调色差异太小，几乎看不出渐变）
  - 自定义单色 / 自定义渐变（沿圆周扫掠，恰好铺满已充能那段弧）
- **深色模式调暗**：夜间把环朝黑色混合（强度 0–80%），避免刺眼
- **低电量呼吸提醒**：低于阈值时正弦呼吸（周期 0.5–5 秒可调），充电中或电量回升自动停止
- **显示范围**：横屏时隐藏；在指定应用中隐藏（应用选择器，支持搜索／全选／清空）
- **数值微调**：每项参数都是 `[−] 输入框 [+]` + 滑杆，可直接输入精确值

## 实现要点

1. **挂载方式**：hook 状态栏窗口视图树（`statusbar/window/StatusBarWindowView` 等四个候选），
   把自绘 View 作为全屏子 View 插进去。状态栏窗口本身已有 `layoutInDisplayCutoutMode=always`，
   子 View 可直接画进挖孔区域；Android 11+ 非系统窗口新开 overlay 会被降级，画不进去。

2. **挖孔几何**：反射 `DisplayCutout#getCutoutPath()`（@hide，返回已变换到屏幕坐标的路径），
   `computeBounds` 得到精确圆心与半径；失败时退回 `getBoundingRects()` + 设置里的 X/Y 微调。

3. **配置通道**：App 每次改动打包成广播推给 SystemUI，SystemUI 存进自己的私有 prefs 再刷新。
   不用 `XSharedPreferences`——跨 SELinux 域读 App 私有目录不可靠，实测读到的永远是默认值。

4. **前台应用**：`ActivityTaskManager.getService().getFocusedRootTaskInfo()`（@hide）；
   只在"隐藏列表非空"时才 1 秒轮询一次。

5. **重启机制**：SystemUI 侧注册受自定义签名权限保护的广播接收器，收到后回执并
   `Process.killProcess(myPid())`，persistent 进程会被系统自动拉起。

## 本机实测记录（Android 16 / LineageOS，1240×2772，挖孔 r=35 @ (620,74)）

| 功能 | 实测结果 |
|---|---|
| 挖孔解析 | `cutout center=(620.0,74.0) radius=35.0`，与 `dumpsys display` 的 cutoutSpec 一致 |
| 首页状态探测 | PONG 回传 `center=(620, 74) r=35.0 px`、隐藏应用数、版本号 |
| 莫奈渐变 | 逐 20° 采样，颜色从 (103,134,142) 平滑过渡到 (78,98,120)，每步 Δ3–4 色阶 |
| 深色调暗 | 同一位置亮度由 (158,206,219) 降到 (103,134,142)（强度 35%） |
| 低电量呼吸 | 连续 6 帧亮度 71.7 → 91.0 → 120.7 → 113.3 → 76.7 → 83.7 |
| 配置持久化 | 推送 pair=3 后重启 SystemUI，新进程日志仍为 `pair=3` |
| 按应用隐藏 | 桌面截图环带蓝色像素 18–19 个，设置应用内 0 个 |
| 应用列表 | 修复包可见性后列出 78 个应用，含第三方 |
| 重启链路 | 广播触发后 SystemUI pid 变化并自动重新注入；普通应用身份发送则 pid 不变 |
| 配置重载 | 32–803 µs（本地 prefs） |

## 关于截图 / 录屏不可见（已放弃，实测结论）

- Android 16 的系统截图不经过 SystemUI 的 `TakeScreenshotService`，hook 该入口收不到回调
- 给状态栏窗口设 `FLAG_SECURE`（`setLayoutParams` 调用成功）后，截图里环与状态栏内容依然可见
- `screencap` 直接抓取 SurfaceFlinger 所有层，更不受影响
- `MediaProjectionManager$Callback` 是抽象类而非接口，无法用动态代理注册事件回调

## 关于横屏隐藏

开关逻辑已生效（横屏时不绘制），但在这台设备上横屏时**本来就看不到环**：
挖孔转到屏幕侧边中部，而宿主状态栏窗口只覆盖顶部 139 px，环被窗口裁剪。

## 构建

**Android Studio**：直接打开本目录，`Build > Build APK`。
**设备端构建**（无需 PC / Android SDK）：见 `tools/README-build.md`。

依赖 `de.robv.android.xposed:api:82` 为 `compileOnly`；未使用 AndroidX；`minSdk 29`。
Manifest 里声明了 `<queries>`（MAIN/LAUNCHER intent），否则 Android 11+ 的包可见性
会让应用选择器只能看到系统应用。

## 关于续航

- 模块单次配置重载 32–803 µs，30 秒一次 → 日均 CPU 时间 < 1 秒
- 不持有 wakelock、不注册 alarm、无网络活动
- `Handler.postDelayed` 在设备休眠时不执行、也不唤醒设备
- 呼吸动画只在低电量且开关打开时运行（约 20 fps，仅重绘环那一小块区域）
- 前台应用轮询只在配置了隐藏列表后启动；SystemUI 是 persistent 应用，不受 Doze / App Standby 限制

## 已知限制

- 设置页拿不到挖孔数据（Android 15+ 不再向普通应用窗口下发 `DisplayCutout`），
  预览用约 12 dp 示意半径；首页可通过模块探测拿到真机挖孔坐标。
- 挖孔区域若被 ROM 的"隐藏挖孔"效果覆盖，环也会被一起遮住。
