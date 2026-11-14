package io.github.eta.cutoutring;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;

/** 个性化：外观尺寸与颜色。 */
public class PersonalizePage extends Page {

    private RingPreviewView preview;
    private RadioGroup modeGroup;
    private RadioGroup powerModeGroup;

    PersonalizePage(MainActivity host) {
        super(host, R.layout.page_personalize);
        buildUi();
    }

    private void buildUi() {
        Context ctx = host;
        Binders.card(ctx, root, R.id.card_appearance, R.id.card_color,
                R.id.card_power, R.id.card_charge_anim);
        Binders.switchTint(ctx, root, host.accent(), R.id.sw_enable, R.id.sw_track,
                R.id.sw_warn, R.id.sw_charging, R.id.sw_charge_anim,
                R.id.sw_power);
        Binders.stepperTheme(root, host.accent(), R.id.st_stroke, R.id.st_gap,
                R.id.st_start, R.id.st_warn_threshold, R.id.st_charge_anim_speed,
                R.id.st_power_max, R.id.st_power_stroke);

        preview = root.findViewById(R.id.preview);
        modeGroup = root.findViewById(R.id.rg_mode);

        int[] radios = {R.id.rb_monet, R.id.rb_monet_gradient, R.id.rb_single, R.id.rb_gradient,
                R.id.rb_power_follow, R.id.rb_power_single, R.id.rb_power_gradient};
        for (int id : radios) {
            android.widget.RadioButton rb = root.findViewById(id);
            if (rb != null) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(host.accent()));
                rb.setTextColor(UiKit.themeColor(ctx, android.R.attr.textColorPrimary, 0xFFEEEEEE));
            }
        }

        Binders.bindSwitch(root, R.id.sw_enable, host.prefs(), Prefs.KEY_ENABLE,
                Prefs.DEF_ENABLE, changed);
        Binders.bindSwitch(root, R.id.sw_track, host.prefs(), Prefs.KEY_TRACK,
                Prefs.DEF_TRACK, changed);

        Binders.bindStepper(root, R.id.st_stroke, R.string.opt_stroke, host.prefs(),
                Prefs.KEY_STROKE_DP, Prefs.DEF_STROKE_DP, 1f, 6f, 0.5f, 1, changed);
        Binders.bindStepper(root, R.id.st_gap, R.string.opt_gap, host.prefs(),
                Prefs.KEY_GAP_DP, Prefs.DEF_GAP_DP, 0f, 12f, 0.5f, 1, changed);
        Binders.bindStepper(root, R.id.st_start, R.string.opt_start, host.prefs(),
                Prefs.KEY_START_DEG, Prefs.DEF_START_DEG, -180f, 180f, 15f, 0, changed);

        int mode = host.prefs().getInt(Prefs.KEY_COLOR_MODE, Prefs.DEF_COLOR_MODE);
        modeGroup.check(radioIdFor(mode));
        updateRows(mode);
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int selected = modeForRadio(checkedId);
                host.prefs().edit().putInt(Prefs.KEY_COLOR_MODE, selected).apply();
                updateRows(selected);
                refreshPreview();
                host.onConfigChanged();
            }
        });

        Binders.bindSwatch(ctx, root, R.id.swatch_single, host.prefs(),
                Prefs.KEY_COLOR_SINGLE, Prefs.DEF_COLOR_SINGLE, null);
        Binders.bindSwatch(ctx, root, R.id.swatch_start, host.prefs(),
                Prefs.KEY_COLOR_START, Prefs.DEF_COLOR_START, null);
        Binders.bindSwatch(ctx, root, R.id.swatch_end, host.prefs(),
                Prefs.KEY_COLOR_END, Prefs.DEF_COLOR_END, null);
        Binders.bindSwatch(ctx, root, R.id.swatch_warn, host.prefs(),
                Prefs.KEY_WARN_COLOR, Prefs.DEF_WARN_COLOR, null);
        Binders.bindSwatch(ctx, root, R.id.swatch_charging, host.prefs(),
                Prefs.KEY_CHARGING_COLOR, Prefs.DEF_CHARGING_COLOR, null);

        Binders.bindSwitch(root, R.id.sw_warn, host.prefs(), Prefs.KEY_WARN_ENABLED,
                Prefs.DEF_WARN_ENABLED, changed);
        Binders.bindSwitch(root, R.id.sw_charging, host.prefs(),
                Prefs.KEY_CHARGING_OVERRIDE, Prefs.DEF_CHARGING_OVERRIDE, changed);
        Binders.bindStepper(root, R.id.st_warn_threshold, R.string.warn_threshold, host.prefs(),
                Prefs.KEY_WARN_THRESHOLD, Prefs.DEF_WARN_THRESHOLD, 0f, 50f, 1f, 0, changed);

        buildMonetPairs();
        initChargeAnim();
        initPower();
    }

    /** 充电功率内环：开关、三种取色方式、尺寸与满圈功率。 */
    private void initPower() {
        powerModeGroup = root.findViewById(R.id.rg_power_mode);

        Switch powerSwitch = root.findViewById(R.id.sw_power);
        powerSwitch.setChecked(host.prefs().getBoolean(Prefs.KEY_POWER_ENABLED,
                Prefs.DEF_POWER_ENABLED));
        powerSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                host.prefs().edit().putBoolean(Prefs.KEY_POWER_ENABLED, isChecked).apply();
                updatePowerOptions();
                refreshPreview();
                host.onConfigChanged();
            }
        });
        updatePowerOptions();

        int mode = host.prefs().getInt(Prefs.KEY_POWER_COLOR_MODE, Prefs.DEF_POWER_COLOR_MODE);
        powerModeGroup.check(powerRadioIdFor(mode));
        updatePowerRows(mode);
        powerModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int selected = powerModeForRadio(checkedId);
                host.prefs().edit().putInt(Prefs.KEY_POWER_COLOR_MODE, selected).apply();
                updatePowerRows(selected);
                refreshPreview();
                host.onConfigChanged();
            }
        });

        Binders.bindSwatch(ctx(), root, R.id.swatch_power, host.prefs(),
                Prefs.KEY_POWER_COLOR, Prefs.DEF_POWER_COLOR, swatchChanged);
        Binders.bindSwatch(ctx(), root, R.id.swatch_power_start, host.prefs(),
                Prefs.KEY_POWER_START, Prefs.DEF_POWER_START, swatchChanged);
        Binders.bindSwatch(ctx(), root, R.id.swatch_power_end, host.prefs(),
                Prefs.KEY_POWER_END, Prefs.DEF_POWER_END, swatchChanged);

        Binders.bindStepper(root, R.id.st_power_max, R.string.opt_power_max, host.prefs(),
                Prefs.KEY_POWER_MAX_W, Prefs.DEF_POWER_MAX_W, 5f, 240f, 5f, 0, changed);
        Binders.bindStepper(root, R.id.st_power_stroke, R.string.opt_power_stroke,
                host.prefs(), Prefs.KEY_POWER_STROKE_DP, Prefs.DEF_POWER_STROKE_DP,
                0.5f, 4f, 0.5f, 1, changed);

        bindPowerStyleToggle();
    }

    /** 「内环样式」默认收起：叠在同一弧上之后，颜色和粗细是进阶项，平时不用一直占屏。 */
    private void bindPowerStyleToggle() {
        final View body = root.findViewById(R.id.power_style_body);
        final TextView toggle = root.findViewById(R.id.btn_power_style);
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

    private Context ctx() {
        return host;
    }

    private final View.OnClickListener swatchChanged = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            refreshPreview();
            host.onConfigChanged();
        }
    };

    private void updatePowerOptions() {
        boolean enabled = host.prefs().getBoolean(Prefs.KEY_POWER_ENABLED,
                Prefs.DEF_POWER_ENABLED);
        Binders.visible(root, R.id.power_options, enabled);
    }

    private void updatePowerRows(int mode) {
        Binders.visible(root, R.id.row_power_single, mode == Prefs.POWER_MODE_SINGLE);
        Binders.visible(root, R.id.row_power_gradient, mode == Prefs.POWER_MODE_GRADIENT);
    }

    private static int powerRadioIdFor(int mode) {
        switch (mode) {
            case Prefs.POWER_MODE_SINGLE:
                return R.id.rb_power_single;
            case Prefs.POWER_MODE_GRADIENT:
                return R.id.rb_power_gradient;
            case Prefs.POWER_MODE_FOLLOW:
            default:
                return R.id.rb_power_follow;
        }
    }

    private static int powerModeForRadio(int checkedId) {
        if (checkedId == R.id.rb_power_single) {
            return Prefs.POWER_MODE_SINGLE;
        }
        if (checkedId == R.id.rb_power_gradient) {
            return Prefs.POWER_MODE_GRADIENT;
        }
        return Prefs.POWER_MODE_FOLLOW;
    }

    private void initChargeAnim() {
        Binders.bindSwitch(root, R.id.sw_charge_anim, host.prefs(),
                Prefs.KEY_CHARGE_ANIM_ENABLED, Prefs.DEF_CHARGE_ANIM_ENABLED, changed);
        Binders.bindStepper(root, R.id.st_charge_anim_speed, R.string.charge_anim_speed,
                host.prefs(), Prefs.KEY_CHARGE_ANIM_SPEED, Prefs.DEF_CHARGE_ANIM_SPEED,
                0.5f, 3f, 0.1f, 1, new Binders.OnChange() {
                    @Override
                    public void onChanged() {
                        refreshPreview();
                        host.onConfigChanged();
                        // 调速度时在预览里放一次动画，直观看到快慢
                        preview.playChargeAnimation();
                    }
                });
    }

    private final Binders.OnChange changed = new Binders.OnChange() {
        @Override
        public void onChanged() {
            refreshPreview();
            host.onConfigChanged();
        }
    };

    /** 四种莫奈配色方案，每个按钮直接画出该方案的渐变预览。 */
    private void buildMonetPairs() {
        LinearLayout container = root.findViewById(R.id.monet_pair_container);
        if (container == null) {
            return;
        }
        container.removeAllViews();
        int size = (int) UiKit.dp(host, 40f);
        int gap = (int) UiKit.dp(host, 8f);
        for (int i = 0; i < MonetColors.PAIR_COUNT; i++) {
            View swatch = new View(host);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.leftMargin = gap;
            container.addView(swatch, params);
            final int pair = i;
            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.prefs().edit().putInt(Prefs.KEY_MONET_PAIR, pair).apply();
                    updateMonetPairs();
                    refreshPreview();
                    host.onConfigChanged();
                }
            });
        }
        updateMonetPairs();
    }

    private void updateMonetPairs() {
        LinearLayout container = root.findViewById(R.id.monet_pair_container);
        if (container == null) {
            return;
        }
        int selected = host.prefs().getInt(Prefs.KEY_MONET_PAIR, Prefs.DEF_MONET_PAIR);
        for (int i = 0; i < container.getChildCount(); i++) {
            GradientDrawable drawable = Binders.gradient(host,
                    MonetColors.gradientPair(host, i), i == selected, host.accent());
            container.getChildAt(i).setBackground(drawable);
        }
    }

    private void updateRows(int mode) {
        Binders.visible(root, R.id.row_monet_palette, mode == Prefs.COLOR_MODE_MONET_GRADIENT);
        Binders.visible(root, R.id.row_single, mode == Prefs.COLOR_MODE_SINGLE);
        Binders.visible(root, R.id.row_gradient, mode == Prefs.COLOR_MODE_GRADIENT);
    }

    private static int radioIdFor(int mode) {
        switch (mode) {
            case Prefs.COLOR_MODE_MONET_GRADIENT:
                return R.id.rb_monet_gradient;
            case Prefs.COLOR_MODE_SINGLE:
                return R.id.rb_single;
            case Prefs.COLOR_MODE_GRADIENT:
                return R.id.rb_gradient;
            case Prefs.COLOR_MODE_MONET:
            default:
                return R.id.rb_monet;
        }
    }

    private static int modeForRadio(int checkedId) {
        if (checkedId == R.id.rb_monet_gradient) {
            return Prefs.COLOR_MODE_MONET_GRADIENT;
        }
        if (checkedId == R.id.rb_single) {
            return Prefs.COLOR_MODE_SINGLE;
        }
        if (checkedId == R.id.rb_gradient) {
            return Prefs.COLOR_MODE_GRADIENT;
        }
        return Prefs.COLOR_MODE_MONET;
    }

    @Override
    public void onShown() {
        refreshPreview();
    }

    void refreshPreview() {
        MonetPalette palette = MonetPalette.of(host, MonetPalette.isDark(host));
        palette.applyPair(host, host.prefs().getInt(Prefs.KEY_MONET_PAIR, Prefs.DEF_MONET_PAIR));
        RingConfig config = RingConfig.load(host.prefs(), host.density(), palette);
        preview.setConfig(config);
        // 预览里用约 62% 的满圈功率示意内环长度
        preview.setDemoWatts(config.powerEnabled ? Math.max(1f, config.powerMaxW * 0.62f) : -1f);

        Binders.visible(root, R.id.row_warn,
                host.prefs().getBoolean(Prefs.KEY_WARN_ENABLED, Prefs.DEF_WARN_ENABLED));
        Binders.visible(root, R.id.st_warn_threshold,
                host.prefs().getBoolean(Prefs.KEY_WARN_ENABLED, Prefs.DEF_WARN_ENABLED));
        Binders.visible(root, R.id.row_charging,
                host.prefs().getBoolean(Prefs.KEY_CHARGING_OVERRIDE, Prefs.DEF_CHARGING_OVERRIDE));
    }
}
