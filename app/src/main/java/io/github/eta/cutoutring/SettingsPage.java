package io.github.eta.cutoutring;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Set;

/** 设置：显示范围、夜间与提醒、位置微调、重启入口。 */
public class SettingsPage extends Page {

    private TextView hiddenSummary;
    private TextView restartStatus;

    SettingsPage(MainActivity host) {
        super(host, R.layout.page_settings);
        buildUi();
    }

    private void buildUi() {
        Context ctx = host;
        Binders.card(ctx, root, R.id.card_scope, R.id.card_night, R.id.card_position,
                R.id.card_restart);
        Binders.switchTint(ctx, root, host.accent(), R.id.sw_hide_landscape,
                R.id.sw_dark_dim, R.id.sw_breath);
        Binders.stepperTheme(root, host.accent(), R.id.st_dark_dim, R.id.st_breath_period,
                R.id.st_offx, R.id.st_offy, R.id.st_radius);

        Binders.bindSwitch(root, R.id.sw_hide_landscape, host.prefs(),
                Prefs.KEY_HIDE_LANDSCAPE, Prefs.DEF_HIDE_LANDSCAPE, changed);
        Binders.bindSwitch(root, R.id.sw_dark_dim, host.prefs(),
                Prefs.KEY_DARK_DIM, Prefs.DEF_DARK_DIM, changed);
        Binders.bindSwitch(root, R.id.sw_breath, host.prefs(),
                Prefs.KEY_BREATH_ENABLED, Prefs.DEF_BREATH_ENABLED, changed);

        Binders.bindStepper(root, R.id.st_dark_dim, R.string.dark_dim_alpha, host.prefs(),
                Prefs.KEY_DARK_DIM_ALPHA, Prefs.DEF_DARK_DIM_ALPHA, 0f, 80f, 5f, 0, changed);
        Binders.bindStepper(root, R.id.st_breath_period, R.string.breath_period, host.prefs(),
                Prefs.KEY_BREATH_PERIOD, Prefs.DEF_BREATH_PERIOD, 0.5f, 5f, 0.1f, 1, changed);

        Binders.bindStepper(root, R.id.st_offx, R.string.opt_offx, host.prefs(),
                Prefs.KEY_OFF_X_DP, Prefs.DEF_OFF_X_DP, -20f, 20f, 0.5f, 1, changed);
        Binders.bindStepper(root, R.id.st_offy, R.string.opt_offy, host.prefs(),
                Prefs.KEY_OFF_Y_DP, Prefs.DEF_OFF_Y_DP, -20f, 20f, 0.5f, 1, changed);
        Binders.bindStepper(root, R.id.st_radius, R.string.opt_radius, host.prefs(),
                Prefs.KEY_RADIUS_DP, Prefs.DEF_RADIUS_DP, 0f, 40f, 0.5f, 1, changed);

        hiddenSummary = root.findViewById(R.id.tv_hidden_summary);
        Button pick = root.findViewById(R.id.btn_pick_apps);
        pick.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                UiKit.withAlpha(host.accent(), 0x33)));
        pick.setTextColor(host.accent());
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.startActivity(new Intent(host, AppPickerActivity.class));
            }
        });

        restartStatus = root.findViewById(R.id.tv_restart_status);
        restartStatus.setTextColor(UiKit.themeColor(ctx,
                android.R.attr.textColorSecondary, 0xFF999999));
        Button restart = root.findViewById(R.id.btn_restart_settings);
        restart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                UiKit.withAlpha(host.accent(), 0x33)));
        restart.setTextColor(host.accent());
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartStatus.setText(R.string.restart_sending);
                try {
                    Intent intent = new Intent(Prefs.ACTION_RESTART);
                    intent.setPackage(Prefs.TARGET_PKG);
                    host.sendBroadcast(intent);
                    restartStatus.setText(R.string.restart_sent);
                } catch (Throwable t) {
                    restartStatus.setText(host.getString(R.string.restart_failed) + "：" + t);
                }
            }
        });

        bindPositionToggle();
    }

    /** 位置微调属于一次性校准，默认收起，换机型时才展开。 */
    private void bindPositionToggle() {
        final View body = root.findViewById(R.id.position_body);
        final TextView toggle = root.findViewById(R.id.btn_position);
        if (body == null || toggle == null) {
            return;
        }
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = body.getVisibility() != View.VISIBLE;
                body.setVisibility(show ? View.VISIBLE : View.GONE);
                toggle.setText(show ? R.string.action_collapse : R.string.action_expand);
            }
        });
    }

    private final Binders.OnChange changed = new Binders.OnChange() {
        @Override
        public void onChanged() {
            host.onConfigChanged();
        }
    };

    @Override
    public void onShown() {
        updateHiddenSummary();
    }

    private void updateHiddenSummary() {
        Set<String> set = host.prefs().getStringSet(Prefs.KEY_HIDDEN_PACKAGES, null);
        int count = set == null ? 0 : set.size();
        hiddenSummary.setText(host.getString(R.string.hidden_apps_summary, count));
    }
}
