package io.github.eta.cutoutring;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.SeekBar;
import android.widget.Switch;

/** 一点界面工具：从主题取色、卡片背景、控件着色。不依赖 AndroidX。 */
public final class UiKit {

    private UiKit() {
    }

    public static float dp(Context ctx, float value) {
        return value * ctx.getResources().getDisplayMetrics().density;
    }

    /** 解析主题属性颜色，失败时用 fallback。 */
    public static int themeColor(Context ctx, int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (ctx.getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                if (tv.resourceId != 0) {
                    return ctx.getColor(tv.resourceId);
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** 强调色：优先主题 colorAccent，其次莫奈主色。 */
    public static int accent(Context ctx) {
        return themeColor(ctx, android.R.attr.colorAccent, MonetColors.uiAccent(ctx));
    }

    public static boolean isDark(Context ctx) {
        int bg = themeColor(ctx, android.R.attr.colorBackground, 0xFF101014);
        return luminance(bg) < 0.5f;
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color)
                + 0.114f * Color.blue(color)) / 255f;
    }

    /** 卡片背景：随深浅色模式自动选半透明白/黑，圆角柔和。 */
    public static GradientDrawable card(Context ctx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(ctx, 22f));
        drawable.setColor(isDark(ctx) ? 0x14FFFFFF : 0x0D000000);
        return drawable;
    }

    /** 圆形色块，带一圈细描边，避免浅色在浅色背景上看不清。 */
    public static GradientDrawable swatch(Context ctx, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(Math.max(1, (int) dp(ctx, 1f)),
                isDark(ctx) ? 0x33FFFFFF : 0x22000000);
        return drawable;
    }

    public static GradientDrawable rounded(Context ctx, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(ctx, radiusDp));
        drawable.setColor(color);
        return drawable;
    }

    /** 只有描边的圆角矩形，用于输入框。 */
    public static GradientDrawable strokeRounded(Context ctx, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(ctx, radiusDp));
        drawable.setColor(0x00000000);
        drawable.setStroke(Math.max(1, (int) dp(ctx, 1.2f)), strokeColor);
        return drawable;
    }

    public static void tint(SeekBar bar, int accent) {
        if (bar == null) {
            return;
        }
        ColorStateList csl = ColorStateList.valueOf(accent);
        try {
            bar.setProgressTintList(csl);
            bar.setThumbTintList(csl);
        } catch (Throwable ignored) {
        }
    }

    public static void tint(Switch sw, int accent) {
        if (sw == null) {
            return;
        }
        try {
            sw.setThumbTintList(ColorStateList.valueOf(accent));
            sw.setTrackTintList(ColorStateList.valueOf(withAlpha(accent, 0x66)));
        } catch (Throwable ignored) {
        }
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static String toHex(int color) {
        return String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF);
    }
}
