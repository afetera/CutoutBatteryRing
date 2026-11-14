package io.github.eta.cutoutring;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

/** 纯 framework 实现的颜色选择器：色相/饱和度/亮度三条渐变滑块 + HEX 输入。 */
public final class ColorPickerDialog {

    public interface Listener {
        void onColorPicked(int color);
    }

    private ColorPickerDialog() {
    }

    public static void show(final Context context, int initialColor, final Listener listener) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null);
        final View preview = root.findViewById(R.id.color_preview);
        final TextView hexLabel = root.findViewById(R.id.color_hex_label);
        final SeekBar sbHue = root.findViewById(R.id.sb_hue);
        final SeekBar sbSat = root.findViewById(R.id.sb_sat);
        final SeekBar sbVal = root.findViewById(R.id.sb_val);
        final EditText etHex = root.findViewById(R.id.et_hex);

        final float[] hsv = new float[3];
        Color.colorToHSV(initialColor | 0xFF000000, hsv);

        final boolean[] updating = {false};
        final int[] current = {Color.HSVToColor(hsv)};

        // SeekBar 自身当作渐变条使用：进度轨道设为透明，背景画渐变
        sbHue.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        sbSat.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        sbVal.setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        sbHue.setBackground(rainbow(context));

        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                hsv[0] = sbHue.getProgress();
                hsv[1] = sbSat.getProgress() / 100f;
                hsv[2] = sbVal.getProgress() / 100f;
                current[0] = Color.HSVToColor(hsv);
                preview.setBackground(UiKit.rounded(context, current[0], 16f));
                String hex = UiKit.toHex(current[0]);
                hexLabel.setText(hex);
                sbSat.setBackground(twoStop(context,
                        Color.HSVToColor(new float[]{hsv[0], 0f, hsv[2]}),
                        Color.HSVToColor(new float[]{hsv[0], 1f, hsv[2]})));
                sbVal.setBackground(twoStop(context,
                        Color.HSVToColor(new float[]{hsv[0], hsv[1], 0.08f}),
                        Color.HSVToColor(new float[]{hsv[0], hsv[1], 1f})));
                if (!hex.equals(etHex.getText().toString().trim())) {
                    updating[0] = true;
                    etHex.setText(hex);
                    etHex.setSelection(etHex.getText().length());
                    updating[0] = false;
                }
            }
        };

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!updating[0]) {
                    apply.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        sbHue.setOnSeekBarChangeListener(seekListener);
        sbSat.setOnSeekBarChangeListener(seekListener);
        sbVal.setOnSeekBarChangeListener(seekListener);

        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updating[0]) {
                    return;
                }
                String text = s.toString().trim();
                if (!text.startsWith("#")) {
                    text = "#" + text;
                }
                if (text.length() != 7) {
                    return;
                }
                try {
                    int color = Color.parseColor(text);
                    float[] parsed = new float[3];
                    Color.colorToHSV(color, parsed);
                    updating[0] = true;
                    sbHue.setProgress(Math.round(parsed[0]));
                    sbSat.setProgress(Math.round(parsed[1] * 100f));
                    sbVal.setProgress(Math.round(parsed[2] * 100f));
                    updating[0] = false;
                    apply.run();
                } catch (Throwable ignored) {
                }
            }
        });

        updating[0] = true;
        sbHue.setProgress(Math.round(hsv[0]));
        sbSat.setProgress(Math.round(hsv[1] * 100f));
        sbVal.setProgress(Math.round(hsv[2] * 100f));
        updating[0] = false;
        apply.run();

        new AlertDialog.Builder(context)
                .setTitle(R.string.picker_title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onColorPicked(current[0]);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    private static Drawable rainbow(Context ctx) {
        int[] colors = new int[13];
        for (int i = 0; i <= 12; i++) {
            colors[i] = Color.HSVToColor(new float[]{i * 30f, 1f, 1f});
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, colors);
        drawable.setCornerRadius(UiKit.dp(ctx, 8f));
        return drawable;
    }

    private static Drawable twoStop(Context ctx, int start, int end) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(UiKit.dp(ctx, 8f));
        return drawable;
    }
}
