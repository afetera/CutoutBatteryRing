package io.github.eta.cutoutring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 把绘制电量环的 View 挂进 SystemUI 状态栏窗口的视图树，并接好控制广播：
 * <ul>
 *   <li>设置页改配置 → 推送整份配置并立即刷新（不用高频轮询）</li>
 *   <li>设置页点"重启 SystemUI" → 回执后自杀重启</li>
 * </ul>
 * 不新开窗口：状态栏窗口本身已获得 layoutInDisplayCutoutMode=always 的绘制许可，
 * 作为它的子 View 可以直接画在挖孔区域，也不需要额外权限。
 */
public final class RingInstaller {

    private static final String[] HOST_CANDIDATES = {
            // Android 14+ / 16
            "com.android.systemui.shade.NotificationShadeWindowView",
            "com.android.systemui.statusbar.window.StatusBarWindowView",
            // Android 12 / 13
            "com.android.systemui.statusbar.phone.NotificationShadeWindowView",
            // 更老的兜底：至少覆盖顶部区域
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
    };

    private static RingOverlayView sInstalled;
    private static BroadcastReceiver sControlReceiver;
    private static boolean sReceiverRegistered;

    private RingInstaller() {
    }

    public static void install(ClassLoader cl) {
        for (String name : HOST_CANDIDATES) {
            try {
                Class<?> clazz = XposedHelpers.findClassIfExists(name, cl);
                if (clazz == null) {
                    XposedBridge.log("[CutoutRing] host not found: " + name);
                    continue;
                }
                XposedBridge.hookAllMethods(clazz, "onFinishInflate", new ViewHook());
                XposedBridge.hookAllMethods(clazz, "onAttachedToWindow", new ViewHook());
                XposedBridge.log("[CutoutRing] hooked host: " + name);
            } catch (Throwable t) {
                XposedBridge.log("[CutoutRing] hook host failed: " + name + " -> " + t);
            }
        }
    }

    private static final class ViewHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object self = param.thisObject;
            if (self instanceof View) {
                tryInstall((View) self);
            }
        }
    }

    private static void tryInstall(final View host) {
        try {
            if (!(host instanceof ViewGroup)) {
                return;
            }
            if (sInstalled != null && sInstalled.isAttachedToWindow()) {
                return;
            }
            host.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!host.isAttachedToWindow()) {
                            return;
                        }
                        if (sInstalled != null && sInstalled.isAttachedToWindow()) {
                            return;
                        }
                        detachOld();
                        RingOverlayView overlay = new RingOverlayView(host.getContext());
                        ((ViewGroup) host).addView(overlay, new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        sInstalled = overlay;
                        XposedBridge.log("[CutoutRing] overlay attached to "
                                + host.getClass().getName() + " hostSize="
                                + host.getWidth() + "x" + host.getHeight());
                        registerControlReceiver(host.getContext());
                    } catch (Throwable t) {
                        XposedBridge.log("[CutoutRing] attach overlay failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] tryInstall failed: " + t);
        }
    }

    private static void detachOld() {
        if (sInstalled != null && sInstalled.getParent() instanceof ViewGroup) {
            try {
                ((ViewGroup) sInstalled.getParent()).removeView(sInstalled);
            } catch (Throwable ignored) {
            }
        }
        sInstalled = null;
    }

    /**
     * 注册控制广播。注册时指定 {@link Prefs#PERM_RESTART}，只有持有该签名级权限的应用
     * （也就是本模块的设置页）能触发；否则第三方 App 可以随意重启 SystemUI。
     */
    private static void registerControlReceiver(Context context) {
        if (sReceiverRegistered || context == null) {
            return;
        }
        Context app = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        sControlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (Prefs.ACTION_RELOAD.equals(action)) {
                    if (sInstalled != null) {
                        sInstalled.applyPushedConfig(intent);
                    }
                    return;
                }
                if (Prefs.ACTION_PING.equals(action)) {
                    try {
                        Intent pong = new Intent(Prefs.ACTION_PONG);
                        pong.setPackage(Prefs.MODULE_PKG);
                        if (sInstalled != null) {
                            String desc = sInstalled.describeCutout();
                            if (desc != null) {
                                pong.putExtra(Prefs.EXTRA_CUTOUT_DESC, desc);
                            }
                            pong.putExtra(Prefs.EXTRA_HIDDEN_APPS, sInstalled.hiddenAppCount());
                        }
                        ctx.sendBroadcast(pong);
                    } catch (Throwable t) {
                        XposedBridge.log("[CutoutRing] send pong failed: " + t);
                    }
                    return;
                }
                if (Prefs.ACTION_RESTART.equals(action)) {
                    XposedBridge.log("[CutoutRing] restart requested by settings app");
                    try {
                        Intent ack = new Intent(Prefs.ACTION_RESTART_ACK);
                        ack.setPackage(Prefs.MODULE_PKG);
                        ctx.sendBroadcast(ack);
                    } catch (Throwable t) {
                        XposedBridge.log("[CutoutRing] send ack failed: " + t);
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            XposedBridge.log("[CutoutRing] killing SystemUI to reload module");
                            Process.killProcess(Process.myPid());
                        }
                    }, 400L);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Prefs.ACTION_RELOAD);
        filter.addAction(Prefs.ACTION_RESTART);
        filter.addAction(Prefs.ACTION_PING);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(sControlReceiver, filter, Prefs.PERM_RESTART, null,
                        Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(sControlReceiver, filter, Prefs.PERM_RESTART, null);
            }
            sReceiverRegistered = true;
            XposedBridge.log("[CutoutRing] control receiver registered");
        } catch (Throwable t) {
            XposedBridge.log("[CutoutRing] register control receiver failed: " + t);
        }
    }
}
