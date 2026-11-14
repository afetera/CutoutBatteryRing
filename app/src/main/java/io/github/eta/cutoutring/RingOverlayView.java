package io.github.eta.cutoutring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;

/**
 * 覆盖在状态栏窗口上的自绘 View：以挖孔为圆心画一圈电量进度环。
 *
 * <p>配置来源是 SystemUI 自己的私有 prefs（由设置页通过广播推送后落盘），
 * 因此 SystemUI 重启后能自行恢复，也不依赖跨进程读文件。
 *
 * <p>开销控制：默认只有 30 秒一次的兜底轮询；前台应用检测仅在配置了
 * "在这些应用中隐藏"之后才启动；呼吸闪烁只在低电量且开关打开时才跑动画循环。
 */
public class RingOverlayView extends View {

    private static final long CONFIG_POLL_INTERVAL_MS = 30000L;
    private static final long FOREGROUND_POLL_INTERVAL_MS = 1000L;
    private static final long BREATH_FRAME_MS = 50L;

    private final RingRenderer renderer = new RingRenderer();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RingConfig config;

    private int level = -1;
    private boolean charging;

    private boolean haveCutout;
    private float cutCx;
    private float cutCy;
    private float cutR;
    private String loggedGeometry;

    private final Set<String> hiddenPackages = new HashSet<>();
    private String foregroundPackage;
    private boolean foregroundPolling;
    private boolean foregroundQueryStopped;

    private boolean breathing;
    private boolean receiverRegistered;

    private final Runnable configPoller = new Runnable() {
        @Override
        public void run() {
            reloadConfig();
            invalidate();
            handler.postDelayed(this, CONFIG_POLL_INTERVAL_MS);
        }
    };

    private final Runnable foregroundPoller = new Runnable() {
        @Override
        public void run() {
            if (hiddenPackages.isEmpty() || foregroundQueryStopped) {
                foregroundPolling = false;
                return;
            }
            String pkg = SystemProbe.getForegroundPackage();
            if (pkg == null && SystemProbe.isForegroundQueryBroken()) {
                foregroundQueryStopped = true;
                foregroundPolling = false;
                XposedBridge.log("[CutoutRing] foreground query unavailable, stop polling");
                return;
            }
            if (pkg != null && !pkg.equals(foregroundPackage)) {
                foregroundPackage = pkg;
                invalidate();
            }
            handler.postDelayed(this, FOREGROUND_POLL_INTERVAL_MS);
        }
    };

    /** 低电量呼吸：约 20fps 驱动，只在真正需要时运行。 */
    private final Runnable breathTicker = new Runnable() {
        @Override
        public void run() {
            if (!breathing || config == null) {
                return;
            }
            long period = Math.max(400L, (long) config.breathPeriodMs);
            float phase = (SystemClock.uptimeMillis() % period) / (float) period;
            float wave = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * phase));
            config.pulseAlpha = 0.35f + 0.65f * wave;
            invalidate();
            handler.postDelayed(this, BREATH_FRAME_MS);
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBattery(intent);
        }
    };

    public RingOverlayView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        reloadConfig();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerBatteryReceiver();
        handler.removeCallbacks(configPoller);
        handler.post(configPoller);
        post(new Runnable() {
            @Override
            public void run() {
                refreshCutout(null);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(configPoller);
        handler.removeCallbacks(foregroundPoller);
        handler.removeCallbacks(breathTicker);
        foregroundPolling = false;
        breathing = false;
        if (receiverRegistered) {
            try {
                getContext().unregisterReceiver(batteryReceiver);
            } catch (Throwable ignored) {
            }
            receiverRegistered = false;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        refreshCutout(insets == null ? null : insets.getDisplayCutout());
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        reloadConfig();
        invalidate();
    }

    // ---------------------------------------------------------------- 配置

    /** 接收设置页推送过来的整份配置，落到 SystemUI 自己的 prefs 再刷新。 */
    public void applyPushedConfig(Intent intent) {
        try {
            android.os.Bundle extras = intent == null ? null : intent.getExtras();
            if (extras == null) {
                return;
            }
            SharedPreferences.Editor editor = getContext()
                    .getSharedPreferences(Prefs.SHARED_NAME, Context.MODE_PRIVATE).edit();
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof String[]) {
                    editor.putStringSet(key, new HashSet<>(Arrays.asList((String[]) value)));
                }
            }
            editor.apply();
            XposedBridge.log("[CutoutRing] config pushed: " + extras.keySet().size() + " keys");
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] apply pushed config failed: " + t);
        }
        reloadConfig();
        invalidate();
    }

    private void reloadConfig() {
        long started = System.nanoTime();
        SharedPreferences local = null;
        try {
            local = getContext().getSharedPreferences(Prefs.SHARED_NAME, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] open local prefs failed: " + t);
        }

        int pair = Prefs.DEF_MONET_PAIR;
        try {
            if (local != null) {
                pair = local.getInt(Prefs.KEY_MONET_PAIR, Prefs.DEF_MONET_PAIR);
            }
        } catch (Throwable ignored) {
        }
        MonetPalette palette = MonetPalette.of(getContext(), MonetPalette.isDark(getContext()));
        palette.applyPair(getContext(), pair);

        float density = getResources().getDisplayMetrics().density;
        config = RingConfig.load(local, density, palette);

        updateHiddenPackages(config.hiddenPackages);
        syncForegroundPoller();
        updateBreathing();

        long costUs = (System.nanoTime() - started) / 1000L;
        XposedBridge.log("[CutoutRing] config reload cost=" + costUs + "us hiddenApps="
                + hiddenPackages.size() + " dark=" + config.darkMode
                + " breath=" + breathing + " pair=" + pair);
    }

    /** 供首页显示模块状态：返回挖孔的圆心与半径描述，未知时返回 null。 */
    public String describeCutout() {
        if (!haveCutout) {
            return null;
        }
        return String.format(java.util.Locale.US, "center=(%.0f, %.0f)  r=%.1f px",
                cutCx, cutCy, cutR);
    }

    /** 当前生效的隐藏应用数量 */
    public int hiddenAppCount() {
        return hiddenPackages.size();
    }

    /** 供首页显示当前是否处于深色模式 */
    public boolean isDarkModeActive() {
        return config != null && config.darkMode;
    }

    private void updateHiddenPackages(Set<String> packages) {
        if (packages == null || packages.isEmpty()) {
            if (!hiddenPackages.isEmpty()) {
                hiddenPackages.clear();
            }
            foregroundPackage = null;
            return;
        }
        if (hiddenPackages.size() != packages.size() || !hiddenPackages.containsAll(packages)) {
            hiddenPackages.clear();
            hiddenPackages.addAll(packages);
            invalidate();
        }
    }

    private void syncForegroundPoller() {
        if (!hiddenPackages.isEmpty() && !foregroundPolling && !foregroundQueryStopped) {
            foregroundPolling = true;
            handler.post(foregroundPoller);
        } else if (hiddenPackages.isEmpty() && foregroundPolling) {
            handler.removeCallbacks(foregroundPoller);
            foregroundPolling = false;
            foregroundPackage = null;
        }
    }

    private void updateBreathing() {
        boolean should = config != null && config.enabled && config.breathEnabled
                && level >= 0 && level <= config.warnThreshold && !charging;
        if (should && !breathing) {
            breathing = true;
            handler.post(breathTicker);
        } else if (!should && breathing) {
            breathing = false;
            handler.removeCallbacks(breathTicker);
            if (config != null) {
                config.pulseAlpha = 1f;
            }
            invalidate();
        }
    }

    private boolean isLandscape() {
        try {
            return getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean shouldShow() {
        if (config == null || !config.enabled) {
            return false;
        }
        if (config.hideLandscape && isLandscape()) {
            return false;
        }
        return hiddenPackages.isEmpty()
                || foregroundPackage == null
                || !hiddenPackages.contains(foregroundPackage);
    }

    // ---------------------------------------------------------------- 电池

    private void registerBatteryReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky = null;
        try {
            sticky = getContext().registerReceiver(batteryReceiver, filter);
        } catch (Throwable t) {
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    sticky = getContext().registerReceiver(batteryReceiver, filter,
                            Context.RECEIVER_NOT_EXPORTED);
                }
            } catch (Throwable t2) {
                XposedBridge.log("[CutoutRing] register battery receiver failed: " + t2);
                return;
            }
        }
        receiverRegistered = true;
        if (sticky != null) {
            updateBattery(sticky);
        } else {
            readBatteryOnce();
        }
    }

    private void readBatteryOnce() {
        try {
            Object svc = getContext().getSystemService(Context.BATTERY_SERVICE);
            if (svc instanceof BatteryManager) {
                int capacity = ((BatteryManager) svc)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity >= 0) {
                    level = capacity;
                    updateBreathing();
                    invalidate();
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] read battery failed: " + t);
        }
    }

    private void updateBattery(Intent intent) {
        if (intent == null) {
            return;
        }
        try {
            int lv = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (lv >= 0 && scale > 0) {
                level = Math.round(lv * 100f / scale);
            }
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            updateBreathing();
            invalidate();
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] update battery failed: " + t);
        }
    }

    // ---------------------------------------------------------------- 几何

    private void refreshCutout(DisplayCutout cutout) {
        DisplayCutout dc = cutout;
        if (dc == null) {
            WindowInsets wi = getRootWindowInsets();
            if (wi != null) {
                dc = wi.getDisplayCutout();
            }
        }
        if (dc == null) {
            haveCutout = false;
            return;
        }

        RectF pathBounds = CutoutPathUtil.getPathBounds(dc);
        if (pathBounds != null) {
            cutCx = pathBounds.centerX();
            cutCy = pathBounds.centerY();
            cutR = Math.min(pathBounds.width(), pathBounds.height()) / 2f;
        } else {
            Rect best = pickTopRect(dc);
            if (best == null) {
                haveCutout = false;
                return;
            }
            cutCx = best.exactCenterX();
            cutR = best.width() / 2f;
            cutCy = best.top + cutR;
        }

        haveCutout = cutR > 0f;
        if (haveCutout) {
            logGeometryOnce();
        }
        invalidate();
    }

    private void logGeometryOnce() {
        String key = Math.round(cutCx) + "," + Math.round(cutCy) + "," + Math.round(cutR);
        if (!key.equals(loggedGeometry)) {
            loggedGeometry = key;
            XposedBridge.log("[CutoutRing] cutout center=(" + cutCx + "," + cutCy
                    + ") radius=" + cutR);
        }
    }

    private static Rect pickTopRect(DisplayCutout dc) {
        Rect best = null;
        List<Rect> rects = dc.getBoundingRects();
        for (Rect r : rects) {
            if (r == null || r.isEmpty()) {
                continue;
            }
            if (best == null || r.top < best.top
                    || (r.top == best.top && r.width() > best.width())) {
                best = r;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!haveCutout || !shouldShow()) {
            return;
        }
        renderer.draw(canvas, config, cutCx, cutCy, cutR, level, charging);
    }
}
