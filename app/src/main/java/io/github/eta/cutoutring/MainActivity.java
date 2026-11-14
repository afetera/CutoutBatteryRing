package io.github.eta.cutoutring;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Map;
import java.util.Set;

/** 底部三页（首页 / 个性化 / 设置）的宿主。 */
public class MainActivity extends Activity {

    public static final String EXTRA_PAGE = "page";

    private SharedPreferences sp;
    private float density;
    private int accent;
    private Handler handler;

    private Page[] pages;
    private BottomNavView nav;
    private int currentPage = -1;

    private final Runnable reloadSender = new Runnable() {
        @Override
        public void run() {
            sendReload();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        applyCutoutMode();
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        accent = UiKit.accent(this);
        handler = new Handler(Looper.getMainLooper());

        FrameLayout container = findViewById(R.id.page_container);
        nav = findViewById(R.id.bottom_nav);
        nav.setAccent(accent);
        nav.setLabels(new String[]{
                getString(R.string.tab_home),
                getString(R.string.tab_personalize),
                getString(R.string.tab_settings)});

        pages = new Page[]{
                new HomePage(this),
                new PersonalizePage(this),
                new SettingsPage(this)};
        for (Page page : pages) {
            page.getView().setVisibility(View.GONE);
            container.addView(page.getView(), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        nav.setOnTabSelected(new BottomNavView.OnTabSelected() {
            @Override
            public void onTabSelected(int index) {
                showPage(index);
            }
        });

        int startPage = 0;
        try {
            startPage = getIntent().getIntExtra(EXTRA_PAGE, 0);
        } catch (Throwable ignored) {
        }
        if (startPage < 0 || startPage >= pages.length) {
            startPage = 0;
        }
        nav.setSelected(startPage);
        showPage(startPage);
    }

    /** 允许通过 {@code --ei page N} 直接打开指定页面。 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        int page = intent == null ? -1 : intent.getIntExtra(EXTRA_PAGE, -1);
        if (page >= 0 && page < pages.length) {
            nav.setSelected(page);
            showPage(page);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentPage >= 0) {
            pages[currentPage].onShown();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(reloadSender);
        super.onDestroy();
    }

    private void showPage(int index) {
        if (index < 0 || index >= pages.length) {
            return;
        }
        if (currentPage >= 0 && currentPage != index) {
            pages[currentPage].onHidden();
            pages[currentPage].getView().setVisibility(View.GONE);
        }
        pages[index].getView().setVisibility(View.VISIBLE);
        pages[index].onShown();
        currentPage = index;
    }

    /** 让窗口延伸到挖孔区域，否则取不到 DisplayCutout（返回 null）。 */
    private void applyCutoutMode() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 供页面使用

    public SharedPreferences prefs() {
        return sp;
    }

    public float density() {
        return density;
    }

    public int accent() {
        return accent;
    }

    /** 设置改动后合并推送一次（250ms 去抖），SystemUI 收到才重新读配置。 */
    public void scheduleReload() {
        handler.removeCallbacks(reloadSender);
        handler.postDelayed(reloadSender, 250L);
    }

    public void sendReload() {
        try {
            Intent intent = new Intent(Prefs.ACTION_RELOAD);
            intent.setPackage(Prefs.TARGET_PKG);
            for (Map.Entry<String, ?> entry : sp.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    intent.putExtra(key, (Boolean) value);
                } else if (value instanceof Float) {
                    intent.putExtra(key, (Float) value);
                } else if (value instanceof Integer) {
                    intent.putExtra(key, (Integer) value);
                } else if (value instanceof String) {
                    intent.putExtra(key, (String) value);
                } else if (value instanceof Set) {
                    Set<?> set = (Set<?>) value;
                    String[] array = new String[set.size()];
                    int index = 0;
                    for (Object item : set) {
                        array[index++] = String.valueOf(item);
                    }
                    intent.putExtra(key, array);
                }
            }
            sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    /** 页面改完外观后调用：刷新当前页预览并推送。 */
    public void onConfigChanged() {
        scheduleReload();
        for (Page page : pages) {
            if (page instanceof PersonalizePage) {
                ((PersonalizePage) page).refreshPreview();
            }
        }
    }
}
