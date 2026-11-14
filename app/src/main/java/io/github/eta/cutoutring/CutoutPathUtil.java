package io.github.eta.cutoutring;

import android.graphics.Path;
import android.graphics.RectF;
import android.view.DisplayCutout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 从 DisplayCutout 取出挖孔的精确几何。
 *
 * <p>公开 API 只给 boundingRects（对圆孔来说是孔的外接矩形，高度会被拉伸到安全区底部），
 * 不足以还原圆心；这里优先反射 @hide 的 {@code DisplayCutout#getCutoutPath()}，
 * 它返回已经过 rotate/offset 变换的屏幕坐标路径，computeBounds 后正好是圆的包围盒。
 */
public final class CutoutPathUtil {

    private CutoutPathUtil() {
    }

    /** @return 屏幕坐标系下的挖孔路径包围盒；无法取得时返回 null。 */
    public static RectF getPathBounds(DisplayCutout cutout) {
        if (cutout == null) {
            return null;
        }
        Path path = resolvePath(cutout);
        if (path == null) {
            return null;
        }
        try {
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            if (bounds.width() <= 0f || bounds.height() <= 0f) {
                return null;
            }
            return bounds;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Path resolvePath(DisplayCutout cutout) {
        // 首选 @hide 的公开方法（Android 13+ 存在，返回已变换到屏幕坐标的副本）
        try {
            Method m = DisplayCutout.class.getMethod("getCutoutPath");
            Object o = m.invoke(cutout);
            if (o instanceof Path) {
                return (Path) o;
            }
        } catch (Throwable ignored) {
            // 方法不存在或被 hidden API 限制，继续尝试字段
        }
        // 退路：直接读字段（坐标语义在不同版本上可能未变换，取到后由调用方校验合理性）
        try {
            Field f = DisplayCutout.class.getDeclaredField("mCutoutPath");
            f.setAccessible(true);
            Object o = f.get(cutout);
            if (o instanceof Path) {
                return (Path) o;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
