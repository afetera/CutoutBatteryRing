package io.github.eta.cutoutring;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ModuleEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }

        // 模块自身进程：只记一条日志，方便确认模块确实被 LSPosed 加载
        if (Prefs.MODULE_PKG.equals(lpparam.packageName)) {
            XposedBridge.log("[CutoutRing] module process hooked (settings app)");
            return;
        }

        if (!Prefs.TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }

        try {
            RingInstaller.install(lpparam.classLoader);
            XposedBridge.log("[CutoutRing] hooked SystemUI");
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] install failed: " + t);
        }
    }
}
