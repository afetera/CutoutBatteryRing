package io.github.eta.cutoutring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 首页：电量环预览、LSPosed 加载状态、更新日志、快速重启 SystemUI。 */
public class HomePage extends Page {

    private static final long PING_TIMEOUT_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private RingPreviewView preview;
    private TextView previewLevelView;
    private TextView statusView;
    private TextView cutoutView;
    private TextView versionView;
    private TextView restartStatus;

    private BroadcastReceiver pongReceiver;
    private boolean probing;

    HomePage(MainActivity host) {
        super(host, R.layout.page_home);
        buildUi();
    }

    private void buildUi() {
        Context ctx = host;
        Binders.card(ctx, root, R.id.card_preview, R.id.card_status,
                R.id.card_changelog, R.id.card_actions);
        Binders.seekTint(ctx, root, host.accent(), R.id.sb_preview);
        Button chargeDemo = root.findViewById(R.id.btn_preview_charge);
        if (chargeDemo != null) {
            chargeDemo.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    UiKit.withAlpha(host.accent(), 0x33)));
            chargeDemo.setTextColor(host.accent());
            chargeDemo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    preview.playChargeDemo();
                }
            });
        }

        preview = root.findViewById(R.id.preview);
        previewLevelView = root.findViewById(R.id.tv_preview_level);
        statusView = root.findViewById(R.id.tv_module_status);
        cutoutView = root.findViewById(R.id.tv_cutout_status);
        versionView = root.findViewById(R.id.tv_version);
        restartStatus = root.findViewById(R.id.tv_restart_status);

        SeekBar seekBar = root.findViewById(R.id.sb_preview);
        final int level = host.prefs().getInt(Prefs.KEY_PREVIEW_LEVEL, Prefs.DEF_PREVIEW_LEVEL);
        seekBar.setProgress(level);
        previewLevelView.setText(ctx.getString(R.string.preview_level, level));
        preview.setPreviewLevel(level);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                host.prefs().edit().putInt(Prefs.KEY_PREVIEW_LEVEL, progress).apply();
                previewLevelView.setText(ctx.getString(R.string.preview_level, progress));
                preview.setPreviewLevel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        Button restart = root.findViewById(R.id.btn_restart);
        restart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(host.accent()));
        restart.setTextColor(contrastOn(host.accent()));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.sendReload();
                host.scheduleReload();
                restartSystemUi();
            }
        });
        restartStatus.setTextColor(UiKit.themeColor(ctx,
                android.R.attr.textColorSecondary, 0xFF999999));

        versionView.setText("v" + appVersion());

        final TextView changelog = root.findViewById(R.id.tv_changelog);
        final TextView changelogToggle = root.findViewById(R.id.btn_changelog);
        if (changelog != null && changelogToggle != null) {
            changelogToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean expanded = changelog.getMaxLines() > 4;
                    changelog.setMaxLines(expanded ? 4 : Integer.MAX_VALUE);
                    changelogToggle.setText(expanded
                            ? R.string.action_expand : R.string.action_collapse);
                }
            });
        }
    }

    private static int contrastOn(int color) {
        double luminance = (0.299 * android.graphics.Color.red(color)
                + 0.587 * android.graphics.Color.green(color)
                + 0.114 * android.graphics.Color.blue(color)) / 255d;
        return luminance > 0.6 ? 0xFF101014 : 0xFFFFFFFF;
    }

    private String appVersion() {
        try {
            PackageInfo info = host.getPackageManager()
                    .getPackageInfo(host.getPackageName(), 0);
            return info.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    @Override
    public void onShown() {
        refreshPreview();
        probeModule();
    }

    @Override
    public void onHidden() {
        unregisterPong();
    }

    void refreshPreview() {
        MonetPalette palette = MonetPalette.of(host, MonetPalette.isDark(host));
        palette.applyPair(host, host.prefs().getInt(Prefs.KEY_MONET_PAIR, Prefs.DEF_MONET_PAIR));
        RingConfig config = RingConfig.load(host.prefs(), host.density(), palette);
        preview.setConfig(config);
        applyCutoutFallback();
    }

    private void applyCutoutFallback() {
        try {
            android.view.WindowInsets wi = host.getWindow().getDecorView().getRootWindowInsets();
            android.view.DisplayCutout dc = wi == null ? null : wi.getDisplayCutout();
            if (dc != null) {
                android.graphics.RectF bounds = CutoutPathUtil.getPathBounds(dc);
                if (bounds != null) {
                    preview.setCutRadius(Math.min(bounds.width(), bounds.height()) / 2f);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        preview.setCutRadius(UiKit.dp(host, 12f));
    }

    // ------------------------------------------------------------ 模块状态探测

    private void probeModule() {
        if (probing) {
            return;
        }
        probing = true;
        statusView.setText(R.string.home_status_probing);
        cutoutView.setText("");
        registerPong();
        try {
            Intent ping = new Intent(Prefs.ACTION_PING);
            ping.setPackage(Prefs.TARGET_PKG);
            host.sendBroadcast(ping);
        } catch (Throwable ignored) {
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (probing) {
                    probing = false;
                    unregisterPong();
                    statusView.setText(R.string.home_status_offline);
                    cutoutView.setText(R.string.home_status_offline_hint);
                }
            }
        }, PING_TIMEOUT_MS);
    }

    private void registerPong() {
        unregisterPong();
        pongReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                probing = false;
                unregisterPong();
                statusView.setText(R.string.home_status_online);
                String desc = intent.getStringExtra(Prefs.EXTRA_CUTOUT_DESC);
                int hidden = intent.getIntExtra(Prefs.EXTRA_HIDDEN_APPS, 0);
                StringBuilder sb = new StringBuilder();
                if (desc != null) {
                    sb.append(host.getString(R.string.home_cutout, desc));
                } else {
                    sb.append(host.getString(R.string.cutout_none));
                }
                sb.append('\n').append(host.getString(R.string.home_hidden_count, hidden));
                cutoutView.setText(sb.toString());
            }
        };
        IntentFilter filter = new IntentFilter(Prefs.ACTION_PONG);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                host.registerReceiver(pongReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                host.registerReceiver(pongReceiver, filter);
            }
        } catch (Throwable t) {
            pongReceiver = null;
        }
    }

    private void unregisterPong() {
        if (pongReceiver == null) {
            return;
        }
        try {
            host.unregisterReceiver(pongReceiver);
        } catch (Throwable ignored) {
        }
        pongReceiver = null;
    }

    // ------------------------------------------------------------ 重启 SystemUI

    private void restartSystemUi() {
        restartStatus.setText(R.string.restart_sending);
        registerPong();   // 顺便复用回执判断模块在线
        try {
            Intent intent = new Intent(Prefs.ACTION_RESTART);
            intent.setPackage(Prefs.TARGET_PKG);
            host.sendBroadcast(intent);
        } catch (Throwable t) {
            restartStatus.setText(host.getString(R.string.restart_failed) + "：" + t);
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (pongReceiver != null) {
                    unregisterPong();
                    restartStatus.setText(R.string.restart_fallback);
                    tryRootRestart();
                }
            }
        }, 1500L);
    }

    private void tryRootRestart() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int code = -1;
                try {
                    Process process = Runtime.getRuntime()
                            .exec(new String[]{"su", "-c", "killall com.android.systemui"});
                    code = process.waitFor();
                } catch (Throwable ignored) {
                }
                final int result = code;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        restartStatus.setText(result == 0
                                ? R.string.restart_ok_root : R.string.restart_failed);
                    }
                });
            }
        }).start();
    }
}
