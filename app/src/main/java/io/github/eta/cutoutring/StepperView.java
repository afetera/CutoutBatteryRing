package io.github.eta.cutoutring;

import android.content.Context;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * 一行参数微调控件：标签 + [−] [数值输入框] [+] 三段式，下面再跟一条滑杆。
 * 三者始终同步，输入框接受手打数值（回车或失焦生效，非法输入回退）。
 */
public class StepperView extends LinearLayout {

    public interface OnValueChangeListener {
        void onValueChanged(float value);
    }

    private static final int SEEK_STEPS = 200;

    private final TextView labelView;
    private final TextView minusButton;
    private final TextView plusButton;
    private final EditText inputView;
    private final SeekBar seekBar;

    private float minValue;
    private float maxValue = 100f;
    private float stepValue = 1f;
    private int decimals = 1;
    private float current;
    private String labelFormat = "%s";
    private OnValueChangeListener listener;
    private boolean updating;

    public StepperView(Context context) {
        this(context, null);
    }

    public StepperView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);

        float d = getResources().getDisplayMetrics().density;
        int gap = (int) (d * 6f);
        int buttonSize = (int) (d * 38f);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        labelView = new TextView(context);
        labelView.setTextSize(13f);
        row.addView(labelView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        minusButton = buildButton(context, "−");
        row.addView(minusButton, new LayoutParams(buttonSize, buttonSize));

        inputView = new EditText(context);
        inputView.setTextSize(14f);
        inputView.setGravity(Gravity.CENTER);
        inputView.setSingleLine(true);
        inputView.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        inputView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LayoutParams inputParams = new LayoutParams((int) (d * 74f), LayoutParams.WRAP_CONTENT);
        inputParams.leftMargin = gap;
        inputParams.rightMargin = gap;
        row.addView(inputView, inputParams);

        plusButton = buildButton(context, "+");
        row.addView(plusButton, new LayoutParams(buttonSize, buttonSize));

        seekBar = new SeekBar(context);
        seekBar.setMax(SEEK_STEPS);
        LayoutParams seekParams = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = (int) (d * 2f);
        addView(seekBar, seekParams);

        minusButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setValue(current - stepValue, true);
            }
        });
        plusButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setValue(current + stepValue, true);
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!updating) {
                    setValue(minValue + (maxValue - minValue) * progress / SEEK_STEPS, true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        inputView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                commitInput();
                return true;
            }
        });
        inputView.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    commitInput();
                }
            }
        });
    }

    private TextView buildButton(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(18f);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    public void applyTheme(int accent) {
        Context ctx = getContext();
        minusButton.setBackground(UiKit.rounded(ctx, UiKit.withAlpha(accent, 0x33), 11f));
        plusButton.setBackground(UiKit.rounded(ctx, UiKit.withAlpha(accent, 0x33), 11f));
        minusButton.setTextColor(accent);
        plusButton.setTextColor(accent);
        inputView.setTextColor(UiKit.themeColor(ctx, android.R.attr.textColorPrimary, 0xFFEEEEEE));
        inputView.setBackground(UiKit.strokeRounded(ctx, UiKit.withAlpha(accent, 0x66), 11f));
        UiKit.tint(seekBar, accent);
    }

    public void configure(String labelFormat, float min, float max, float step,
                          int decimals, OnValueChangeListener listener) {
        this.labelFormat = labelFormat;
        this.minValue = min;
        this.maxValue = max;
        this.stepValue = step > 0f ? step : 1f;
        this.decimals = decimals;
        this.listener = listener;
    }

    public float getValue() {
        return current;
    }

    public void setValue(float value, boolean notify) {
        float clamped = Math.max(minValue, Math.min(maxValue, value));
        clamped = Math.round(clamped / stepValue) * stepValue;
        clamped = Math.max(minValue, Math.min(maxValue, clamped));

        current = clamped;
        updating = true;
        labelView.setText(labelFormat.replace("%s", displayValue()));
        inputView.setText(displayValue());
        seekBar.setProgress(toProgress(clamped));
        updating = false;
        updateButtons();

        if (notify && listener != null) {
            listener.onValueChanged(clamped);
        }
    }

    private String displayValue() {
        return decimals > 0
                ? String.format(Locale.US, "%." + decimals + "f", current)
                : String.valueOf(Math.round(current));
    }

    private void commitInput() {
        String text = inputView.getText().toString().trim().replace(',', '.');
        try {
            setValue(Float.parseFloat(text), true);
        } catch (Throwable t) {
            setValue(current, false);
        }
    }

    private void updateButtons() {
        boolean canMinus = current > minValue + stepValue / 2f;
        boolean canPlus = current < maxValue - stepValue / 2f;
        minusButton.setEnabled(canMinus);
        plusButton.setEnabled(canPlus);
        minusButton.setAlpha(canMinus ? 1f : 0.35f);
        plusButton.setAlpha(canPlus ? 1f : 0.35f);
    }

    private int toProgress(float value) {
        if (maxValue <= minValue) {
            return 0;
        }
        float ratio = (value - minValue) / (maxValue - minValue);
        return Math.max(0, Math.min(SEEK_STEPS, Math.round(ratio * SEEK_STEPS)));
    }
}
