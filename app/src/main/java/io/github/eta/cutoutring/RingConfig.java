package io.github.eta.cutoutring;

import android.content.SharedPreferences;

/** 一份完整的环样式配置快照（长度已换算为 px），App 预览与 SystemUI 绘制共用。 */
public final class RingConfig {

    public boolean enabled = Prefs.DEF_ENABLE;
    public boolean showTrack = Prefs.DEF_TRACK;

    public float strokePx;
    public float gapPx;
    public float radiusOverridePx;
    public float offXPx;
    public float offYPx;
    public float startDeg = Prefs.DEF_START_DEG;

    public int colorMode = Prefs.DEF_COLOR_MODE;
    public int colorSingle = Prefs.DEF_COLOR_SINGLE;
    public int colorStart = Prefs.DEF_COLOR_START;
    public int colorEnd = Prefs.DEF_COLOR_END;
    public int monetPair = Prefs.DEF_MONET_PAIR;

    public boolean warnEnabled = Prefs.DEF_WARN_ENABLED;
    public float warnThreshold = Prefs.DEF_WARN_THRESHOLD;
    public int warnColor = Prefs.DEF_WARN_COLOR;

    public boolean chargingOverride = Prefs.DEF_CHARGING_OVERRIDE;
    public int chargingColor = Prefs.DEF_CHARGING_COLOR;

    /** 莫奈色（由调用方按主题与方案解析后填入） */
    public int monetMain = Prefs.DEF_COLOR_SINGLE;
    public int monetAlt = Prefs.DEF_COLOR_END;
    public int monetGradStart = Prefs.DEF_COLOR_START;
    public int monetGradEnd = Prefs.DEF_COLOR_END;

    /** 深色模式：由调用方在加载时填入；darkDim 为"加暗色遮罩"开关 */
    public boolean darkMode;
    public boolean darkDim = Prefs.DEF_DARK_DIM;
    /** 0..1，调暗比例 */
    public float darkDimAlpha = Prefs.DEF_DARK_DIM_ALPHA / 100f;

    /** 低电量呼吸 */
    public boolean breathEnabled = Prefs.DEF_BREATH_ENABLED;
    public float breathPeriodMs = Prefs.DEF_BREATH_PERIOD * 1000f;
    /** 运行时由绘制方每帧写入的透明度系数 */
    public float pulseAlpha = 1f;

    public boolean hideLandscape = Prefs.DEF_HIDE_LANDSCAPE;

    /** 插电旋转动画 */
    public boolean chargeAnimEnabled = Prefs.DEF_CHARGE_ANIM_ENABLED;
    public float chargeAnimSpeed = Prefs.DEF_CHARGE_ANIM_SPEED;
    /** 运行时：动画中的绘制起始角（NaN 表示不在动画中） */
    public float animStartDeg = Float.NaN;
    /** 运行时：充电色混入比例 0..1，用于插电/拔电的颜色渐变过渡 */
    public float chargeBlend;


    /** 在这些应用里不显示电量环 */
    public java.util.Set<String> hiddenPackages = java.util.Collections.emptySet();

    private RingConfig() {
    }

    public static RingConfig load(SharedPreferences sp, float density, MonetPalette palette) {
        if (density <= 0f) {
            density = 1f;
        }
        RingConfig c = new RingConfig();
        c.enabled = getBool(sp, Prefs.KEY_ENABLE, Prefs.DEF_ENABLE);
        c.showTrack = getBool(sp, Prefs.KEY_TRACK, Prefs.DEF_TRACK);

        c.strokePx = getFloat(sp, Prefs.KEY_STROKE_DP, Prefs.DEF_STROKE_DP) * density;
        c.gapPx = getFloat(sp, Prefs.KEY_GAP_DP, Prefs.DEF_GAP_DP) * density;
        c.radiusOverridePx = getFloat(sp, Prefs.KEY_RADIUS_DP, Prefs.DEF_RADIUS_DP) * density;
        c.offXPx = getFloat(sp, Prefs.KEY_OFF_X_DP, Prefs.DEF_OFF_X_DP) * density;
        c.offYPx = getFloat(sp, Prefs.KEY_OFF_Y_DP, Prefs.DEF_OFF_Y_DP) * density;
        c.startDeg = getFloat(sp, Prefs.KEY_START_DEG, Prefs.DEF_START_DEG);

        c.colorMode = getInt(sp, Prefs.KEY_COLOR_MODE, Prefs.DEF_COLOR_MODE);
        c.colorSingle = getInt(sp, Prefs.KEY_COLOR_SINGLE, Prefs.DEF_COLOR_SINGLE);
        c.colorStart = getInt(sp, Prefs.KEY_COLOR_START, Prefs.DEF_COLOR_START);
        c.colorEnd = getInt(sp, Prefs.KEY_COLOR_END, Prefs.DEF_COLOR_END);
        c.monetPair = getInt(sp, Prefs.KEY_MONET_PAIR, Prefs.DEF_MONET_PAIR);

        c.warnEnabled = getBool(sp, Prefs.KEY_WARN_ENABLED, Prefs.DEF_WARN_ENABLED);
        c.warnThreshold = getFloat(sp, Prefs.KEY_WARN_THRESHOLD, Prefs.DEF_WARN_THRESHOLD);
        c.warnColor = getInt(sp, Prefs.KEY_WARN_COLOR, Prefs.DEF_WARN_COLOR);

        c.chargingOverride = getBool(sp, Prefs.KEY_CHARGING_OVERRIDE,
                Prefs.DEF_CHARGING_OVERRIDE);
        c.chargingColor = getInt(sp, Prefs.KEY_CHARGING_COLOR, Prefs.DEF_CHARGING_COLOR);

        c.darkDim = getBool(sp, Prefs.KEY_DARK_DIM, Prefs.DEF_DARK_DIM);
        c.darkDimAlpha = getFloat(sp, Prefs.KEY_DARK_DIM_ALPHA, Prefs.DEF_DARK_DIM_ALPHA) / 100f;
        c.breathEnabled = getBool(sp, Prefs.KEY_BREATH_ENABLED, Prefs.DEF_BREATH_ENABLED);
        c.breathPeriodMs = getFloat(sp, Prefs.KEY_BREATH_PERIOD, Prefs.DEF_BREATH_PERIOD) * 1000f;
        c.hideLandscape = getBool(sp, Prefs.KEY_HIDE_LANDSCAPE, Prefs.DEF_HIDE_LANDSCAPE);
        c.chargeAnimEnabled = getBool(sp, Prefs.KEY_CHARGE_ANIM_ENABLED,
                Prefs.DEF_CHARGE_ANIM_ENABLED);
        c.chargeAnimSpeed = getFloat(sp, Prefs.KEY_CHARGE_ANIM_SPEED,
                Prefs.DEF_CHARGE_ANIM_SPEED);


        if (palette != null) {
            c.darkMode = palette.dark;
            c.monetMain = palette.main;
            c.monetAlt = palette.alt;
            c.monetGradStart = palette.gradientStart;
            c.monetGradEnd = palette.gradientEnd;
        }

        try {
            java.util.Set<String> set = sp == null ? null
                    : sp.getStringSet(Prefs.KEY_HIDDEN_PACKAGES, null);
            c.hiddenPackages = (set == null || set.isEmpty())
                    ? java.util.Collections.<String>emptySet()
                    : new java.util.HashSet<>(set);
        } catch (Throwable t) {
            c.hiddenPackages = java.util.Collections.emptySet();
        }
        return c;
    }

    // ------------------------------------------------------------ 颜色解析

    public boolean isGradient() {
        return colorMode == Prefs.COLOR_MODE_GRADIENT
                || colorMode == Prefs.COLOR_MODE_MONET_GRADIENT;
    }

    /** 绘制时实际使用的起始角：动画中会被临时覆盖。 */
    public float effectiveStartDeg() {
        return Float.isNaN(animStartDeg) ? startDeg : animStartDeg;
    }

    /** 插电旋转动画的时长（毫秒），速度倍率越大越短。 */
    public long chargeAnimDurationMs() {
        float speed = chargeAnimSpeed < 0.1f ? 0.1f : chargeAnimSpeed;
        return (long) (1800f / speed);
    }

    public int gradientStartColor() {
        return colorMode == Prefs.COLOR_MODE_MONET_GRADIENT ? monetGradStart : colorStart;
    }

    public int gradientEndColor() {
        return colorMode == Prefs.COLOR_MODE_MONET_GRADIENT ? monetGradEnd : colorEnd;
    }

    /** 单色模式下应该用的颜色（渐变模式下作为兜底）。 */
    public int solidColor() {        switch (colorMode) {
            case Prefs.COLOR_MODE_SINGLE:
                return colorSingle;
            case Prefs.COLOR_MODE_GRADIENT:
                return colorStart;
            case Prefs.COLOR_MODE_MONET_GRADIENT:
                return monetGradStart;
            case Prefs.COLOR_MODE_MONET:
            default:
                return monetMain;
        }
    }

    private static boolean getBool(SharedPreferences sp, String key, boolean def) {
        if (sp == null) {
            return def;
        }
        try {
            return sp.getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static float getFloat(SharedPreferences sp, String key, float def) {
        if (sp == null) {
            return def;
        }
        try {
            return sp.getFloat(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static int getInt(SharedPreferences sp, String key, int def) {
        if (sp == null) {
            return def;
        }
        try {
            return sp.getInt(key, def);
        } catch (Throwable t) {
            return def;
        }
    }
}
