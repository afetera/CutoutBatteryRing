package io.github.eta.cutoutring;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 自绘底部导航栏：三个等宽标签，选中项用强调色着色并画一条顶部指示条。
 * 图标用简单几何路径绘制，避免引入图标库。
 */
public class BottomNavView extends View {

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final int TAB_COUNT = 3;

    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private String[] labels = {"首页", "个性化", "设置"};
    private int selected;
    private int accentColor = 0xFF8AB4F8;
    private int normalColor = 0xFF9AA0A6;

    private OnTabSelected listener;

    public BottomNavView(Context context) {
        this(context, null);
    }

    public BottomNavView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(d * 1.7f);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTextSize(11.5f * getResources().getDisplayMetrics().scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
        indicatorPaint.setStyle(Paint.Style.FILL);
        dividerPaint.setColor(0x1AFFFFFF);
    }

    public void setLabels(String[] labels) {
        if (labels != null && labels.length == TAB_COUNT) {
            this.labels = labels;
            invalidate();
        }
    }

    public void setAccent(int accent) {
        this.accentColor = accent;
        this.normalColor = UiKit.themeColor(getContext(),
                android.R.attr.textColorSecondary, 0xFF9AA0A6);
        invalidate();
    }

    public void setSelected(int index) {
        if (index >= 0 && index < TAB_COUNT && index != selected) {
            selected = index;
            invalidate();
        }
    }

    public int getSelected() {
        return selected;
    }

    public void setOnTabSelected(OnTabSelected listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float tabWidth = width / (float) TAB_COUNT;

        canvas.drawRect(0f, 0f, width, 1f, dividerPaint);

        for (int i = 0; i < TAB_COUNT; i++) {
            float centerX = tabWidth * i + tabWidth / 2f;
            boolean active = i == selected;
            int color = active ? accentColor : normalColor;

            iconPaint.setColor(color);
            textPaint.setColor(color);

            float iconCenterY = height * 0.38f;
            drawIcon(canvas, i, centerX, iconCenterY);

            canvas.drawText(labels[i], centerX, height * 0.78f, textPaint);

            if (active) {
                indicatorPaint.setColor(accentColor);
                float indicatorWidth = tabWidth * 0.28f;
                float r = 1.6f * getResources().getDisplayMetrics().density;
                canvas.drawRoundRect(centerX - indicatorWidth / 2f, 1f,
                        centerX + indicatorWidth / 2f, 1f + r * 2f, r, r, indicatorPaint);
            }
        }
    }

    /** 0=房子 1=调色板 2=滑块 */
    private void drawIcon(Canvas canvas, int index, float cx, float cy) {
        float d = getResources().getDisplayMetrics().density;
        float s = d * 8f;
        path.reset();
        switch (index) {
            case 0:
                path.moveTo(cx - s, cy);
                path.lineTo(cx, cy - s * 0.85f);
                path.lineTo(cx + s, cy);
                path.moveTo(cx - s * 0.68f, cy - s * 0.1f);
                path.lineTo(cx - s * 0.68f, cy + s * 0.75f);
                path.lineTo(cx + s * 0.68f, cy + s * 0.75f);
                path.lineTo(cx + s * 0.68f, cy - s * 0.1f);
                canvas.drawPath(path, iconPaint);
                break;
            case 1:
                canvas.drawCircle(cx - s * 0.15f, cy, s * 0.85f, iconPaint);
                iconPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - s * 0.55f, cy - s * 0.3f, s * 0.16f, iconPaint);
                canvas.drawCircle(cx + s * 0.2f, cy - s * 0.45f, s * 0.16f, iconPaint);
                canvas.drawCircle(cx + s * 0.5f, cy + s * 0.15f, s * 0.16f, iconPaint);
                iconPaint.setStyle(Paint.Style.STROKE);
                break;
            default:
                float half = s * 0.85f;
                for (int i = 0; i < 3; i++) {
                    float y = cy - half + i * half;
                    canvas.drawLine(cx - half, y, cx + half, y, iconPaint);
                }
                iconPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - half * 0.4f, cy - half, d * 1.6f, iconPaint);
                canvas.drawCircle(cx + half * 0.5f, cy, d * 1.6f, iconPaint);
                canvas.drawCircle(cx - half * 0.1f, cy + half, d * 1.6f, iconPaint);
                iconPaint.setStyle(Paint.Style.STROKE);
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = (int) (event.getX() / (getWidth() / (float) TAB_COUNT));
            if (index >= 0 && index < TAB_COUNT) {
                setSelected(index);
                if (listener != null) {
                    listener.onTabSelected(index);
                }
                performClick();
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
