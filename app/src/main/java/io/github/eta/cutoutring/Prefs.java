package io.github.eta.cutoutring;

/** 模块与被注入的 SystemUI 进程共用的设置键与默认值。 */
public final class Prefs {

    public static final String MODULE_PKG = "io.github.eta.cutoutring";
    public static final String TARGET_PKG = "com.android.systemui";
    /** SharedPreferences 文件名，需与 SettingsActivity 中一致。 */
    public static final String NAME = "config";
    /**
     * SystemUI 侧自己保存配置用的名字。
     *
     * <p>配置不由 SystemUI 直接读 App 私有目录（跨 SELinux 域读文件不可靠），
     * 而是 App 通过广播把整份配置推过去、SystemUI 存进自己的私有 prefs，
     * 这样 SystemUI 重启后也能自己恢复，且不依赖任何跨进程文件访问。
     */
    public static final String SHARED_NAME = "cutout_ring_config";

    // 开关
    public static final String KEY_ENABLE = "enable";
    public static final String KEY_TRACK = "track";

    // 几何
    public static final String KEY_STROKE_DP = "stroke_dp";
    public static final String KEY_GAP_DP = "gap_dp";
    public static final String KEY_RADIUS_DP = "radius_dp";
    public static final String KEY_OFF_X_DP = "off_x_dp";
    public static final String KEY_OFF_Y_DP = "off_y_dp";
    public static final String KEY_START_DEG = "start_deg";

    // 颜色
    public static final String KEY_COLOR_MODE = "color_mode";
    public static final String KEY_COLOR_SINGLE = "color_single";
    public static final String KEY_COLOR_START = "color_start";
    public static final String KEY_COLOR_END = "color_end";
    public static final String KEY_MONET_PAIR = "monet_pair";
    public static final String KEY_WARN_ENABLED = "warn_enabled";
    public static final String KEY_WARN_THRESHOLD = "warn_threshold";
    public static final String KEY_WARN_COLOR = "warn_color";
    public static final String KEY_CHARGING_OVERRIDE = "charging_override";
    public static final String KEY_CHARGING_COLOR = "charging_color";

    // 深色模式 / 提醒
    public static final String KEY_DARK_DIM = "dark_dim";
    public static final String KEY_DARK_DIM_ALPHA = "dark_dim_alpha";
    public static final String KEY_BREATH_ENABLED = "breath_enabled";
    public static final String KEY_BREATH_PERIOD = "breath_period";
    public static final String KEY_HIDE_LANDSCAPE = "hide_landscape";
    /** 插入充电器时的旋转动画 */
    public static final String KEY_CHARGE_ANIM_ENABLED = "charge_anim_enabled";
    public static final String KEY_CHARGE_ANIM_SPEED = "charge_anim_speed";

    /** 0 = 跟随系统莫奈取色，1 = 自定义单色，2 = 自定义渐变，3 = 莫奈渐变 */
    public static final int COLOR_MODE_MONET = 0;
    public static final int COLOR_MODE_SINGLE = 1;
    public static final int COLOR_MODE_GRADIENT = 2;
    public static final int COLOR_MODE_MONET_GRADIENT = 3;

    // 跨进程广播（App -> SystemUI）
    public static final String ACTION_RESTART = "io.github.eta.cutoutring.action.RESTART_SYSTEMUI";
    public static final String ACTION_RESTART_ACK = "io.github.eta.cutoutring.action.RESTART_ACK";
    /** 只有持有该权限的应用才能触发重启，防止第三方 App 随意重启 SystemUI。 */
    public static final String PERM_RESTART = "io.github.eta.cutoutring.permission.RESTART_SYSTEMUI";
    /** 设置变更后通知 SystemUI 立即刷新（取代高频轮询） */
    public static final String ACTION_RELOAD = "io.github.eta.cutoutring.action.RELOAD";
    /** 首页用来探测模块是否已注入 SystemUI */
    public static final String ACTION_PING = "io.github.eta.cutoutring.action.PING";
    public static final String ACTION_PONG = "io.github.eta.cutoutring.action.PONG";
    /** PONG 附带的状态字段 */
    public static final String EXTRA_CUTOUT_DESC = "cutout_desc";
    public static final String EXTRA_HIDDEN_APPS = "hidden_apps";

    // 在这些应用中隐藏
    public static final String KEY_HIDDEN_PACKAGES = "hidden_packages";

    // 设置页预览（仅 App 内使用）
    public static final String KEY_PREVIEW_LEVEL = "preview_level";
    public static final String KEY_PREVIEW_CHARGING = "preview_charging";
    public static final int DEF_PREVIEW_LEVEL = 72;

    // ---- 默认值 ----
    public static final boolean DEF_ENABLE = true;
    public static final boolean DEF_TRACK = true;

    public static final float DEF_STROKE_DP = 2.5f;
    public static final float DEF_GAP_DP = 3f;
    public static final float DEF_RADIUS_DP = 0f;
    public static final float DEF_OFF_X_DP = 0f;
    public static final float DEF_OFF_Y_DP = 0f;
    public static final float DEF_START_DEG = -90f;

    public static final int DEF_COLOR_MODE = COLOR_MODE_MONET;
    public static final int DEF_COLOR_SINGLE = 0xFF8AB4F8;
    public static final int DEF_COLOR_START = 0xFFA78BFA;
    public static final int DEF_COLOR_END = 0xFF22D3EE;

    public static final boolean DEF_WARN_ENABLED = true;
    public static final float DEF_WARN_THRESHOLD = 15f;
    public static final int DEF_WARN_COLOR = 0xFFF44336;

    public static final boolean DEF_CHARGING_OVERRIDE = true;
    public static final int DEF_CHARGING_COLOR = 0xFF4CAF50;

    public static final int DEF_MONET_PAIR = 0;
    public static final boolean DEF_DARK_DIM = true;
    /** 深色模式下的调暗强度（百分比） */
    public static final float DEF_DARK_DIM_ALPHA = 35f;
    public static final boolean DEF_BREATH_ENABLED = false;
    /** 呼吸周期（秒） */
    public static final float DEF_BREATH_PERIOD = 1.5f;
    public static final boolean DEF_HIDE_LANDSCAPE = false;
    /** 插电旋转动画默认开启 */
    public static final boolean DEF_CHARGE_ANIM_ENABLED = true;
    /** 旋转速度倍率（1.0 = 基础 1.8 秒转两圈） */
    public static final float DEF_CHARGE_ANIM_SPEED = 1f;

    private Prefs() {
    }
}
