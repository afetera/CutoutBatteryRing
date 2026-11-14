package io.github.eta.cutoutring;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 设置页里的实时预览：用与 SystemUI 完全相同的 {@link RingRenderer} 绘制，
 * 等比放大以便看清细节（整体 canvas 缩放，比例与真机一致）。
 */
public class RingPreviewView extends View {

    private final RingRenderer renderer = new RingRenderer();
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RingConfig config;
    private int level = 72;
    private boolean charging;
    /** 真机挖孔半径（px），由 Activity 从 WindowInsets 取得 */
    private float cutR = 35f;
    private float scale = 2.2f;

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
