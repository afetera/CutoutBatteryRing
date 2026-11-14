package io.github.eta.cutoutring;

import android.content.Context;
import android.content.res.Configuration;

/** 一份解析好的莫奈调色板快照（App 预览与 SystemUI 绘制各自解析后填入配置）。 */
public final class MonetPalette {

    public int main = Prefs.DEF_COLOR_SINGLE;
    public int alt = Prefs.DEF_COLOR_END;
    public int gradientStart = Prefs.DEF_COLOR_SINGLE;
    public int gradientEnd = Prefs.DEF_COLOR_END;
    public boolean dark;

    public static MonetPalette of(Context ctx, boolean dark) {
        MonetPalette palette = new MonetPalette();
        palette.dark = dark;
        palette.main = MonetColors.accent1(ctx);
        palette.alt = MonetColors.accent3(ctx);
        return palette;
    }

    /** 按选定的渐变方案填充两端色。 */
    public void applyPair(Context ctx, int pair) {
        int[] colors = MonetColors.gradientPair(ctx, pair);
        gradientStart = colors[0];
        gradientEnd = colors[1];
    }

    public static boolean isDark(Context ctx) {
        try {
            return (ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }
}
