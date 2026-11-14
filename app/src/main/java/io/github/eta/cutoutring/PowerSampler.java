package io.github.eta.cutoutring;

import android.content.Context;
import android.os.BatteryManager;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * 读取当前充电功率（单位 W）。
 *
 * <p>电压优先用电池广播里的 {@code EXTRA_VOLTAGE}（mV，不需要额外权限），
 * 其次读 sysfs；电流优先用 {@link BatteryManager#BATTERY_PROPERTY_CURRENT_NOW}
 * （SystemUI 持有 BATTERY_STATS），再退回 sysfs。
 *
 * <p>单位没有统一标准：同一批内核里 current_now 可能是 µA 也可能是 mA，
 * voltage_now 可能是 µV 也可能是 mV。这里用数量级启发式判断，避免算出来的
 * 功率差 1000 倍。
 */
public final class PowerSampler {

    private static final String SYS_BATTERY = "/sys/class/power_supply/battery";
    private static final String SYS_USB = "/sys/class/power_supply/usb";

    /** 低于这个值就当没在充电（待机功耗约 1W，充电至少几瓦） */
    private static final float MIN_VALID_WATTS = 0.15f;
    private static final double MAX_VALID_WATTS = 500d;

    private PowerSampler() {
    }

    /**
     * @param context   任意上下文（SystemUI 进程内最好）
     * @param voltageMv 电池广播里的电压（mV），未知传 -1
     * @return 功率（W）；未知或不可用时返回 -1
     */
    public static float readWatts(Context context, int voltageMv) {
        double volts = voltageMv > 0 ? voltageMv / 1000d : 0d;
        if (!(volts > 0d)) {
            volts = toVolts(sysfsNumber(SYS_BATTERY + "/voltage_now"));
        }
        if (!(volts > 0d)) {
            volts = toVolts(sysfsNumber(SYS_USB + "/voltage_now"));
        }
        if (!(volts > 0d)) {
            return -1f;
        }

        double current = batteryManagerCurrent(context);
        if (Double.isNaN(current)) {
            current = sysfsNumber(SYS_BATTERY + "/current_now");
        }
        if (Double.isNaN(current)) {
            current = sysfsNumber(SYS_USB + "/current_now");
        }
        if (Double.isNaN(current)) {
            return -1f;
        }

        double watts = toAmps(current) * volts;
        if (watts < MIN_VALID_WATTS || watts > MAX_VALID_WATTS) {
            return -1f;
        }
        return (float) watts;
    }

    private static double batteryManagerCurrent(Context context) {
        try {
            Object svc = context == null ? null : context.getSystemService(Context.BATTERY_SERVICE);
            if (!(svc instanceof BatteryManager)) {
                return Double.NaN;
            }
            int current = ((BatteryManager) svc)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (current == Integer.MIN_VALUE || current == 0) {
                return Double.NaN;
            }
            return current;
        } catch (Throwable t) {
            return Double.NaN;
        }
    }

    /** 电流：> 20000 视为 µA，否则视为 mA。20A 与 20mA 都不是充电场景。 */
    private static double toAmps(double raw) {
        double abs = Math.abs(raw);
        return abs > 20000d ? abs / 1_000_000d : abs / 1000d;
    }

    /** 电压：> 100000 视为 µV，否则视为 mV。 */
    private static double toVolts(double raw) {
        if (Double.isNaN(raw) || raw <= 0d) {
            return 0d;
        }
        return raw > 100000d ? raw / 1_000_000d : raw / 1000d;
    }

    private static double sysfsNumber(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();
            if (line == null) {
                return Double.NaN;
            }
            return Double.parseDouble(line.trim());
        } catch (Throwable t) {
            return Double.NaN;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
