package io.github.eta.cutoutring;

import android.content.ComponentName;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * SystemUI 侧查询前台应用包名：
 * {@code ActivityTaskManager.getService().getFocusedRootTaskInfo()}（@hide，
 * SystemUI 持有 REAL_GET_TASKS 权限）。查询失败会永久停用，不影响电量环本身。
 */
public final class SystemProbe {

    private static Object sAtm;
    private static Method sGetFocusedRootTaskInfo;
    private static boolean sForegroundBroken;

    private SystemProbe() {
    }

    /** @return 前台应用包名；查询失败返回 null 并停用该查询。 */
    public static String getForegroundPackage() {
        if (sForegroundBroken) {
            return null;
        }
        try {
            if (sAtm == null) {
                Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
                Method getService = atmClass.getMethod("getService");
                sAtm = getService.invoke(null);
                sGetFocusedRootTaskInfo = sAtm.getClass().getMethod("getFocusedRootTaskInfo");
            }
            Object info = sGetFocusedRootTaskInfo.invoke(sAtm);
            if (info == null) {
                return null;
            }
            Field topActivity = info.getClass().getField("topActivity");
            Object component = topActivity.get(info);
            if (component instanceof ComponentName) {
                return ((ComponentName) component).getPackageName();
            }
            return null;
        } catch (Throwable t) {
            sForegroundBroken = true;
            return null;
        }
    }

    public static boolean isForegroundQueryBroken() {
        return sForegroundBroken;
    }
}
