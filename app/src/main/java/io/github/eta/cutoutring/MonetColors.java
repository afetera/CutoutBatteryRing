package io.github.eta.cutoutring;

import android.content.Context;
import android.os.Build;

/**
 * 读取系统莫奈（Material You）调色板。
 *
 * <p>Android 12+ 把这些颜色以 {@code android.R.color.system_accent*} 暴露出来，
 * 取值随壁纸变化；取不到时（API < 31 或 ROM 未生成）退回内置默认色。
 * 三组强调色各取 200 / 400 两个色调，可组合出若干套渐变方案。
 */
public final class MonetColors {

    /** 渐变方案数量 */
    public static final int PAIR_COUNT = 4;

    private MonetColors() {
    }

    public static int accent1(Context ctx) {
        return resolve(ctx, android.R.color.system_accent1_200, 0xFF8AB4F8);
    }

    public static int accent2(Context ctx) {
        return resolve(ctx, android.R.color.system_accent2_200, 0xFF7FDBCA);
    }

    public static int accent3(Context ctx) {
        return resolve(ctx, android.R.color.system_accent3_200, 0xFFC58BF2);
    }

    public static int accent1Deep(Context ctx) {
        return resolve(ctx, android.R.color.system_accent1_400, 0xFF5285D6);
    }

    public static int accent2Deep(Context ctx) {
        return resolve(ctx, android.R.color.system_accent2_400, 0xFF3FA894);
    }

    public static int accent3Deep(Context ctx) {
        return resolve(ctx, android.R.color.system_accent3_400, 0xFF9A6BD0);
    }

    /** 界面用强调色（比环色更深一点，用于控件着色） */
    public static int uiAccent(Context ctx) {
        return resolve(ctx, android.R.color.system_accent1_500, 0xFF5B8DEF);
    }

    /**
     * 某一套莫奈渐变方案的两端色。
     *
     * <p>刻意用"浅色调 → 深色调"的组合：同一色调（如都用 200）在不同强调色之间差异很小，
     * 画出来几乎看不出渐变。
     */
    public static int[] gradientPair(Context ctx, int pair) {
        switch (pair) {
            case 1:
                return new int[]{accent2(ctx), accent3Deep(ctx)};
            case 2:
                return new int[]{accent1(ctx), accent3Deep(ctx)};
            case 3:
                return new int[]{accent1Deep(ctx), accent3(ctx)};
            case 0:
            default:
                return new int[]{accent1(ctx), accent2Deep(ctx)};
        }
    }

    private static int resolve(Context ctx, int resId, int fallback) {
        if (ctx == null || Build.VERSION.SDK_INT < 31) {
            return fallback;
        }
        try {
            return ctx.getColor(resId);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
