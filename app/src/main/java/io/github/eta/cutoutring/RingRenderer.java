package io.github.eta.cutoutring;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * 电量环的绘制核心，App 内的实时预览与 SystemUI 里的实际绘制共用同一份逻辑，
 * 保证"设置页看到的"和"状态栏上出现的"一致。
 *
 * <p>这里还负责两个后处理：深色模式下的调暗（防止夜间刺眼）与低电量呼吸的透明度。
 */
public final class RingRenderer {

    private static final int TRACK_COLOR = 0x33FFFFFF;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint powerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF powerRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Matrix powerMatrix = new Matrix();

    private Shader cachedShader;
    private String cachedKey;
    private Shader cachedPowerShader;
    private String cachedPowerKey;

    public RingRenderer() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(TRACK_COLOR);
        powerPaint.setStyle(Paint.Style.STROKE);
        powerPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * @param cx     挖孔圆心 x（px）
     * @param cy     挖孔圆心 y（px）
     * @param cutR   挖孔半径（px）
     * @param level  电量 0-100，小于 0 表示未知
     */
    public void draw(Canvas canvas, RingConfig cfg, float cx, float cy, float cutR,
                     int level, boolean charging) {
        if (cfg == null || !cfg.enabled || level < 0 || cfg.strokePx <= 0f) {
            return;
        }

        float radius = (cfg.radiusOverridePx > 0f ? cfg.radiusOverridePx : cutR + cfg.gapPx)
                + cfg.strokePx / 2f;
        float x = cx + cfg.offXPx;
        float y = cy + cfg.offYPx;
        rect.set(x - radius, y - radius, x + radius, y + radius);

        arcPaint.setStrokeWidth(cfg.strokePx);
        trackPaint.setStrokeWidth(cfg.strokePx);
        arcPaint.setAlpha(255);
        trackPaint.setAlpha(255);

        if (cfg.showTrack) {
            trackPaint.setColor(effectColor(cfg, TRACK_COLOR));
            canvas.drawArc(rect, 0f, 360f, false, trackPaint);
        }

        float pct = Math.max(0f, Math.min(100f, level)) / 100f;
        float sweep = Math.max(0.6f, 360f * pct);
        float startDeg = cfg.effectiveStartDeg();

        boolean warn = cfg.warnEnabled && level <= cfg.warnThreshold;
        boolean gradient = cfg.isGradient() && !warn;

        if (gradient) {
            arcPaint.setShader(sweepShader(cfg, x, y, sweep, startDeg));
        } else {
            arcPaint.setShader(null);
            arcPaint.setColor(effectColor(cfg, resolveColor(cfg, level, charging)));
        }
        canvas.drawArc(rect, startDeg, sweep, false, arcPaint);
    }

    /**
     * 画充电功率：与电量环**同一半径、同一条弧**，功率弧叠加在电量弧上。
     * 由于充电功率通常不会跑满，这样比画两个同心环更紧凑，也更容易一眼对比。
     *
     * @param watts    当前功率（W），小于等于 0 时不画
     * @param level    电量 0-100（跟随模式取色用）
     * @param charging 是否在充电（跟随模式下取充电色）
     */
    public void drawPower(Canvas canvas, RingConfig cfg, float cx, float cy, float cutR,
                          float watts, int level, boolean charging) {
        if (cfg == null || !cfg.powerEnabled || cfg.powerStrokePx <= 0f || watts <= 0f) {
            return;
        }

        float radius = (cfg.radiusOverridePx > 0f
                ? cfg.radiusOverridePx
                : cutR + Math.max(0f, cfg.gapPx)) + cfg.strokePx / 2f;
        float x = cx + cfg.offXPx;
        float y = cy + cfg.offYPx;
        powerRect.set(x - radius, y - radius, x + radius, y + radius);

        powerPaint.setStrokeWidth(cfg.powerStrokePx);
        powerPaint.setAlpha(255);

        float pct = Math.max(0f, Math.min(1f, watts / cfg.powerMaxW));
        if (pct <= 0f) {
            return;
        }
        float sweep = Math.max(0.8f, 360f * pct);
        float startDeg = cfg.effectiveStartDeg();

        if (cfg.powerColorMode == Prefs.POWER_MODE_GRADIENT) {
            powerPaint.setShader(powerShader(cfg, x, y, sweep, startDeg));
        } else if (cfg.powerColorMode == Prefs.POWER_MODE_SINGLE) {
            powerPaint.setShader(null);
            powerPaint.setColor(effectColor(cfg, cfg.powerColor));
        } else {
            powerPaint.setShader(null);
            powerPaint.setColor(effectColor(cfg, resolveColor(cfg, level, charging)));
        }
        canvas.drawArc(powerRect, startDeg, sweep, false, powerPaint);
    }

    public int resolveColor(RingConfig cfg, int level, boolean charging) {
        int base;
        if (cfg.warnEnabled && level <= cfg.warnThreshold) {
            base = cfg.warnColor;
        } else {
            base = cfg.solidColor();
        }
        return applyChargeBlend(cfg, base);
    }

    /**
     * 把颜色按 {@code chargeBlend} 混向充电色，实现插电/拔电的颜色过渡：
     * 0 = 原色，1 = 纯充电色。
     */
    public int applyChargeBlend(RingConfig cfg, int color) {
        if (!cfg.chargingOverride || cfg.chargeBlend <= 0.001f) {
            return color;
        }
        return blend(color, cfg.chargingColor, Math.min(1f, cfg.chargeBlend));
    }

    /** 深色模式调暗 + 呼吸透明度。 */
    public int effectColor(RingConfig cfg, int color) {
        int result = color;
        if (cfg.darkMode && cfg.darkDim) {
            float ratio = Math.max(0f, Math.min(0.85f, cfg.darkDimAlpha));
            result = blend(result, 0xFF000000, ratio);
        }
        if (cfg.pulseAlpha < 0.999f) {
            int alpha = Math.round(Color.alpha(result) * Math.max(0.05f, cfg.pulseAlpha));
            result = (result & 0x00FFFFFF) | (alpha << 24);
        }
        return result;
    }

    public static int blend(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        int a = Math.round(Color.alpha(from) * (1f - r) + Color.alpha(to) * r);
        int red = Math.round(Color.red(from) * (1f - r) + Color.red(to) * r);
        int green = Math.round(Color.green(from) * (1f - r) + Color.green(to) * r);
        int blue = Math.round(Color.blue(from) * (1f - r) + Color.blue(to) * r);
        return Color.argb(a, red, green, blue);
    }

    private boolean useGradient(RingConfig cfg, int level, boolean charging) {
        if (!cfg.isGradient()) {
            return false;
        }
        if (charging && cfg.chargingOverride) {
            return false;
        }
        return !(cfg.warnEnabled && level <= cfg.warnThreshold);
    }

    /**
     * 沿圆周的扫掠渐变：起止色恰好铺满已充能的那段弧（而不是整圈），
     * 这样 40% 电量也能看到完整的颜色过渡。
     */
    private Shader sweepShader(RingConfig cfg, float cx, float cy, float sweep,
                               float startDeg) {
        // 渐变两端同样按 chargeBlend 混向充电色，插电过渡时不会跳变
        int startColor = effectColor(cfg, applyChargeBlend(cfg, cfg.gradientStartColor()));
        int endColor = effectColor(cfg, applyChargeBlend(cfg, cfg.gradientEndColor()));
        float frac = Math.max(0.01f, Math.min(1f, sweep / 360f));
        String key = startColor + ":" + endColor + ":" + Math.round(sweep) + ":"
                + Math.round(cx) + ":" + Math.round(cy) + ":" + Math.round(startDeg);
        if (cachedShader != null && key.equals(cachedKey)) {
            return cachedShader;
        }
        SweepGradient gradient = new SweepGradient(cx, cy,
                new int[]{startColor, endColor}, new float[]{0f, frac});
        matrix.setRotate(startDeg, cx, cy);
        gradient.setLocalMatrix(matrix);
        cachedShader = gradient;
        cachedKey = key;
        return gradient;
    }

    /** 内圈专用渐变：独立矩阵与缓存，避免和外圈共用 Matrix 互相覆盖。 */
    private Shader powerShader(RingConfig cfg, float cx, float cy, float sweep,
                               float startDeg) {
        int startColor = effectColor(cfg, cfg.powerStart);
        int endColor = effectColor(cfg, cfg.powerEnd);
        float frac = Math.max(0.01f, Math.min(1f, sweep / 360f));
        String key = startColor + ":" + endColor + ":" + Math.round(sweep) + ":"
                + Math.round(cx) + ":" + Math.round(cy) + ":" + Math.round(startDeg);
        if (cachedPowerShader != null && key.equals(cachedPowerKey)) {
            return cachedPowerShader;
        }
        SweepGradient gradient = new SweepGradient(cx, cy,
                new int[]{startColor, endColor}, new float[]{0f, frac});
        powerMatrix.setRotate(startDeg, cx, cy);
        gradient.setLocalMatrix(powerMatrix);
        cachedPowerShader = gradient;
        cachedPowerKey = key;
        return gradient;
    }
}
