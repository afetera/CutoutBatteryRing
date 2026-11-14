package io.github.eta.cutoutring;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;

/** 设置类页面的通用绑定工具：开关、色块、数值微调、卡片背景。 */
public final class Binders {

    public interface OnChange {
        void onChanged();
    }

    private Binders() {
    }

    public static void card(Context ctx, View root, int... ids) {
        for (int id : ids) {
            View view = root.findViewById(id);
            if (view != null) {
                view.setBackground(UiKit.card(ctx));
            }
        }
    }

    public static void switchTint(Context ctx, View root, int accent, int... ids) {
        for (int id : ids) {
            UiKit.tint((Switch) root.findViewById(id), accent);
        }
    }

    public static void seekTint(Context ctx, View root, int accent, int... ids) {
        for (int id : ids) {
            UiKit.tint((SeekBar) root.findViewById(id), accent);
        }
    }

    public static void stepperTheme(View root, int accent, int... ids) {
        for (int id : ids) {
            StepperView view = root.findViewById(id);
            if (view != null) {
                view.applyTheme(accent);
            }
        }
    }

    public static void bindSwitch(View root, int id, final SharedPreferences sp,
                                  final String key, final boolean def, final OnChange onChange) {
        Switch sw = root.findViewById(id);
        if (sw == null) {
            return;
        }
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sp.edit().putBoolean(key, isChecked).apply();
                if (onChange != null) {
                    onChange.onChanged();
                }
            }
        });
    }

    public static void bindStepper(View root, int id, int labelRes, final SharedPreferences sp,
                                   final String key, float def, float min, float max,
                                   float step, int decimals, final OnChange onChange) {
        StepperView view = root.findViewById(id);
        if (view == null) {
            return;
        }
        view.configure(root.getContext().getString(labelRes), min, max, step, decimals,
                new StepperView.OnValueChangeListener() {
                    @Override
                    public void onValueChanged(float value) {
                        sp.edit().putFloat(key, value).apply();
                        if (onChange != null) {
                            onChange.onChanged();
                        }
                    }
                });
        view.setValue(sp.getFloat(key, def), false);
    }

    public static void bindSwatch(final Context ctx, View root, int id, final SharedPreferences sp,
                                  final String key, final int def, final View.OnClickListener onPick) {
        final View swatch = root.findViewById(id);
        if (swatch == null) {
            return;
        }
        swatch.setBackground(UiKit.swatch(ctx, sp.getInt(key, def)));
        swatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialog.show(ctx, sp.getInt(key, def), new ColorPickerDialog.Listener() {
                    @Override
                    public void onColorPicked(int picked) {
                        sp.edit().putInt(key, picked).apply();
                        swatch.setBackground(UiKit.swatch(ctx, picked));
                        if (onPick != null) {
                            onPick.onClick(v);
                        }
                    }
                });
            }
        });
    }

    public static GradientDrawable gradient(Context ctx, int[] colors, boolean selected,
                                            int accent) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, colors);
        drawable.setCornerRadius(UiKit.dp(ctx, 10f));
        drawable.setStroke(
                selected ? (int) UiKit.dp(ctx, 2f) : Math.max(1, (int) UiKit.dp(ctx, 1f)),
                selected ? accent : UiKit.withAlpha(0xFFFFFF, 0x33));
        return drawable;
    }

    public static void visible(View root, int id, boolean show) {
        View view = root.findViewById(id);
        if (view != null) {
            view.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
