package com.minios.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hafif ana ekran (launcher). Zamanlayici, animasyon ya da arka plan servisi yok.
 * Uygulama listesi tek seferde arka planda yuklenir; sadece uygulama
 * yuklenip silindiginde yenilenir.
 */
public class MainActivity extends Activity {

    static class App {
        String label, key, pkg, cls;
        Drawable icon;
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BaseAdapter adapter;
    private EditText search;
    private GridView grid;
    private boolean dirty = true;
    private boolean started = false;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            dirty = true;
            if (started) {
                dirty = false;
                load();
            }
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace('\u0131', 'i').replace("\u0307", "");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(0x66000000);
        frame.setFitsSystemWindows(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(8), dp(14), dp(8));
        frame.addView(col, new FrameLayout.LayoutParams(-1, -1));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(56);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        col.addView(time, new LinearLayout.LayoutParams(-2, -2));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextSize(16);
        date.setTextColor(0xCCFFFFFF);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.bottomMargin = dp(12);
        col.addView(date, dlp);

        search = new EditText(this);
        search.setHint("Uygulama ara…");
        search.setHintTextColor(0x99FFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(24));
        search.setBackground(bg);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.bottomMargin = dp(12);
        col.addView(search, slp);

        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(14));
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1f));

        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    LinearLayout l = new LinearLayout(MainActivity.this);
                    l.setOrientation(LinearLayout.VERTICAL);
                    l.setGravity(Gravity.CENTER_HORIZONTAL);
                    ImageView iv = new ImageView(MainActivity.this);
                    l.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
                    TextView tv = new TextView(MainActivity.this);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(12);
                    tv.setSingleLine(true);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(dp(2), dp(4), dp(2), 0);
                    l.addView(tv, new LinearLayout.LayoutParams(-1, -2));
                    l.setTag(new View[]{iv, tv});
                    v = l;
                }
                View[] t = (View[]) v.getTag();
                App a = shown.get(i);
                ((ImageView) t[0]).setImageDrawable(a.icon);
                ((TextView) t[1]).setText(a.label);
                return v;
            }
        };
        grid.setAdapter(adapter);

        grid.setOnItemClickListener((parent, view, pos, id) -> launch(shown.get(pos)));
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + shown.get(pos).pkg)));
            } catch (Exception ignored) { }
            return true;
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void afterTextChanged(Editable e) { filter(e.toString()); }
        });
        search.setOnEditorActionListener((tv, actionId, ev) -> {
            if (!shown.isEmpty()) launch(shown.get(0));
            return true;
        });

        setContentView(frame);
        frame.setFocusableInTouchMode(true);
        frame.requestFocus();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override
    protected void onStart() {
        super.onStart();
        started = true;
        if (dirty) {
            dirty = false;
            load();
        }
    }

    @Override
    protected void onStop() {
        started = false;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        io.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        search.setText("");
        grid.setSelection(0);
        search.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    @Override
    public void onBackPressed() {
        if (search.length() > 0) search.setText("");
        // Ana ekranda geri tusu bir sey yapmaz.
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
        }
    }

    private void filter(String q) {
        String s = norm(q.trim());
        shown.clear();
        for (App a : all) {
            if (s.isEmpty() || a.key.contains(s)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private void load() {
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(q, 0);
            List<App> list = new ArrayList<>();
            for (ResolveInfo r : infos) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                App a = new App();
                a.label = String.valueOf(r.loadLabel(pm));
                a.key = norm(a.label);
                a.pkg = p;
                a.cls = r.activityInfo.name;
                a.icon = r.loadIcon(pm);
                list.add(a);
            }
            final Collator c = Collator.getInstance(new Locale("tr", "TR"));
            Collections.sort(list, (x, y) -> c.compare(x.label, y.label));
            ui.post(() -> {
                all.clear();
                all.addAll(list);
                filter(search.getText().toString());
            });
        });
    }
}
