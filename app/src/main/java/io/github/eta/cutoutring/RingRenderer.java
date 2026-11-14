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
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private Shader cachedShader;
    private String cachedKey;

    public RingRenderer() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(TRACK_COLOR);
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

        if (useGradient(cfg, level, charging)) {
            arcPaint.setShader(sweepShader(cfg, x, y, sweep));
        } else {
            arcPaint.setShader(null);
            arcPaint.setColor(effectColor(cfg, resolveColor(cfg, level, charging)));
        }
        canvas.drawArc(rect, cfg.startDeg, sweep, false, arcPaint);
    }

    /** 当前状态下应该用哪种颜色：警示色 / 充电色 优先于模式色。 */
    public int resolveColor(RingConfig cfg, int level, boolean charging) {
        if (charging && cfg.chargingOverride) {
            return cfg.chargingColor;
        }
        if (cfg.warnEnabled && level <= cfg.warnThreshold) {
            return cfg.warnColor;
        }
        return cfg.solidColor();
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
    private Shader sweepShader(RingConfig cfg, float cx, float cy, float sweep) {
        int startColor = effectColor(cfg, cfg.gradientStartColor());
        int endColor = effectColor(cfg, cfg.gradientEndColor());
        float frac = Math.max(0.01f, Math.min(1f, sweep / 360f));
        String key = startColor + ":" + endColor + ":" + Math.round(sweep) + ":"
                + Math.round(cx) + ":" + Math.round(cy) + ":" + Math.round(cfg.startDeg);
        if (cachedShader != null && key.equals(cachedKey)) {
            return cachedShader;
        }
        SweepGradient gradient = new SweepGradient(cx, cy,
                new int[]{startColor, endColor}, new float[]{0f, frac});
        matrix.setRotate(cfg.startDeg, cx, cy);
        gradient.setLocalMatrix(matrix);
        cachedShader = gradient;
        cachedKey = key;
        return gradient;
    }
}
