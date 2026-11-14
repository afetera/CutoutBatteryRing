package io.github.eta.cutoutring;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * 设置页里的实时预览：用与 SystemUI 完全相同的 {@link RingRenderer} 绘制，
 * 等比放大以便看清细节（整体 canvas 缩放，比例与真机一致）。
 */
public class RingPreviewView extends View {

    private final RingRenderer renderer = new RingRenderer();
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RingConfig config;
    private int level = 72;
    private boolean charging;
    /** 真机挖孔半径（px），由 Activity 从 WindowInsets 取得 */
    private float cutR = 35f;
    private float scale = 2.2f;

    private boolean animating;
    private long animStart;
    private boolean demoRunning;

    private final Runnable chargeAnimTicker = new Runnable() {
        @Override
        public void run() {
            if (!animating || config == null) {
                return;
            }
            long duration = Math.max(200L, config.chargeAnimDurationMs());
            long elapsed = SystemClock.uptimeMillis() - animStart;
            float t = Math.min(1f, elapsed / (float) duration);
            float eased = 1f - (float) Math.pow(1f - t, 3);
            config.animStartDeg = config.startDeg + 720f * eased;
            invalidate();
            if (t < 1f) {
                handler.postDelayed(this, 24L);
            } else {
                animating = false;
                config.animStartDeg = Float.NaN;
                invalidate();
            }
        }
    };

    public RingPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holePaint.setColor(0xFF000000);
    }

    public void setConfig(RingConfig config) {
        this.config = config;
        invalidate();
    }

    public void setPreviewLevel(int level) {
        this.level = level;
        invalidate();
    }

    public void setPreviewCharging(boolean charging) {
        this.charging = charging;
        invalidate();
    }

    public void setCutRadius(float cutR) {
        if (cutR > 0f) {
            this.cutR = cutR;
        }
        invalidate();
    }

    public void setZoom(float scale) {
        if (scale > 0.2f) {
            this.scale = scale;
        }
        invalidate();
    }

    /** 在预览里播一次插电旋转动画，方便调速度时看到效果。 */
    public void playChargeAnimation() {
        if (config == null || !config.chargeAnimEnabled) {
            return;
        }
        animating = true;
        animStart = SystemClock.uptimeMillis();
        handler.removeCallbacks(chargeAnimTicker);
        handler.post(chargeAnimTicker);
    }

    /**
     * 播放一次完整的"插入充电器"演示：先变绿，再旋转两圈，结束后恢复原来的配色。
     * 可以反复点击重复观看。动画被关掉时退化为单纯切换绿色提示。
     */
    public void playChargeDemo() {
        if (config == null || demoRunning) {
            return;
        }
        if (!config.chargeAnimEnabled) {
            charging = !charging;
            invalidate();
            return;
        }
        demoRunning = true;
        charging = true;
        invalidate();
        playChargeAnimation();
        long duration = config.chargeAnimDurationMs();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                charging = false;
                demoRunning = false;
                invalidate();
            }
        }, duration + 150L);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(chargeAnimTicker);
        animating = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null) {
            return;
        }
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scale, scale);
        canvas.drawCircle(0f, 0f, cutR, holePaint);
        renderer.draw(canvas, config, 0f, 0f, cutR, level, charging);
        canvas.restore();
    }
}
