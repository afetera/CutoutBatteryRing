package io.github.eta.cutoutring;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 选择"在这些应用里隐藏电量环"。 */
public class AppPickerActivity extends Activity {

    private static final class AppEntry {
        String label;
        String packageName;
        Drawable icon;
        boolean checked;
    }

    private SharedPreferences sp;
    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();
    private AppAdapter adapter;
    private TextView summaryView;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_app_picker);
        sp = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);

        int accent = UiKit.accent(this);
        summaryView = findViewById(R.id.picker_summary);
        EditText search = findViewById(R.id.picker_search);
        ListView list = findViewById(R.id.picker_list);

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = shownApps.get(position);
            entry.checked = !entry.checked;
            adapter.notifyDataSetChanged();
            save();
        });

        View addAll = findViewById(R.id.picker_all);
        View clearAll = findViewById(R.id.picker_none);
        addAll.setOnClickListener(v -> setAll(true));
        clearAll.setOnClickListener(v -> setAll(false));
        addAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                UiKit.withAlpha(accent, 0x33)));
        clearAll.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                UiKit.withAlpha(accent, 0x33)));

        search.setBackground(UiKit.strokeRounded(this, UiKit.withAlpha(accent, 0x66), 12f));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        loadApps();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dirty) {
            dirty = false;
            try {
                Intent intent = new Intent(Prefs.ACTION_RELOAD);
                intent.setPackage(Prefs.TARGET_PKG);
                sendBroadcast(intent);
            } catch (Throwable ignored) {
            }
        }
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> selected = new HashSet<>(
                sp.getStringSet(Prefs.KEY_HIDDEN_PACKAGES, Collections.<String>emptySet()));
        List<ResolveInfo> resolved = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);

        Set<String> seen = new HashSet<>();
        List<AppEntry> loaded = new ArrayList<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg == null || pkg.equals(getPackageName()) || !seen.add(pkg)) {
                continue;
            }
            AppEntry entry = new AppEntry();
            entry.packageName = pkg;
            try {
                entry.label = String.valueOf(info.loadLabel(pm));
                entry.icon = info.loadIcon(pm);
            } catch (Throwable t) {
                entry.label = pkg;
            }
            entry.checked = selected.contains(pkg);
            loaded.add(entry);
        }
        Collections.sort(loaded, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        allApps.clear();
        allApps.addAll(loaded);
        applyFilter("");
        updateSummary();
    }

    private void applyFilter(String keyword) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.US);
        shownApps.clear();
        for (AppEntry entry : allApps) {
            if (key.isEmpty()
                    || entry.label.toLowerCase(Locale.US).contains(key)
                    || entry.packageName.toLowerCase(Locale.US).contains(key)) {
                shownApps.add(entry);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void setAll(boolean checked) {
        for (AppEntry entry : allApps) {
            entry.checked = checked;
        }
        save();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void save() {
        Set<String> set = new HashSet<>();
        for (AppEntry entry : allApps) {
            if (entry.checked) {
                set.add(entry.packageName);
            }
        }
        sp.edit().putStringSet(Prefs.KEY_HIDDEN_PACKAGES, set).apply();
        dirty = true;
        updateSummary();
    }

    private void updateSummary() {
        int count = 0;
        for (AppEntry entry : allApps) {
            if (entry.checked) {
                count++;
            }
        }
        if (summaryView != null) {
            summaryView.setText(getString(R.string.picker_summary, count, allApps.size()));
        }
    }

    private final class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return shownApps.size();
        }

        @Override
        public Object getItem(int position) {
            return shownApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null ? convertView
                    : LayoutInflater.from(AppPickerActivity.this)
                    .inflate(R.layout.item_app, parent, false);
            AppEntry entry = shownApps.get(position);
            ((TextView) view.findViewById(R.id.item_label)).setText(entry.label);
            ((TextView) view.findViewById(R.id.item_package)).setText(entry.packageName);
            ImageView icon = view.findViewById(R.id.item_icon);
            if (entry.icon != null) {
                icon.setImageDrawable(entry.icon);
            } else {
                icon.setImageDrawable(null);
            }
            ((CheckBox) view.findViewById(R.id.item_check)).setChecked(entry.checked);
            return view;
        }
    }
}
