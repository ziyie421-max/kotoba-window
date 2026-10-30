package com.kotoba.window;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;

/** Shell for the three surfaces: the card, the library, the settings. */
public class MainActivity extends Activity {

    private static final int CARD = 0, LIBRARY = 1, SETTINGS = 2;
    private static final int EXPORT = 10, IMPORT = 11;

    private Store store;
    private StudyView study;
    private Ui.Scheme s;

    private FrameLayout content;
    private View studyPage;
    private int page = CARD;
    private boolean ready;
    private String libraryQuery = "";
    private NavItem navCard, navLibrary, navSettings;

    private JSONObject restoreWord;
    private boolean restoreRevealed, restorePractice;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Crash.install(this);
        try {
            start();
        } catch (Throwable error) {
            Crash.write(this, error);
            showFatal(error);
        }
    }

    private void start() {
        s = Ui.theme(true);
        safeBars(true);
        showLoading();
        new Thread(() -> {
            try {
                Store loaded = Store.get(this);
                runOnUiThread(() -> {
                    store = loaded;
                    ready = false;
                    s = Ui.theme(dark());
                    safeBars(dark());
                    try {
                        build();
                        ready = true;
                        afterStart();
                    } catch (Throwable error) {
                        Crash.write(this, error);
                        showFatal(error);
                    }
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    s = Ui.theme(true);
                    showError(e.getMessage() == null ? "未知错误" : e.getMessage());
                });
            }
        }).start();
    }

    /** System bar styling is decoration: it must never be able to stop the app. */
    private void safeBars(boolean dark) {
        try {
            systemBars(dark);
        } catch (Throwable ignored) {
        }
    }

    /** Surfaces a crash from the previous run, once, when startup succeeds again. */
    private void afterStart() {
        if (!ready) return;
        String previous = Crash.read(this);
        if (previous.isEmpty()) return;
        Sheet.show(this, s, "上次启动出错了",
                previous.trim(), true, "复制", () -> {
                    copy(previous);
                    Crash.clear(this);
                }, "知道了", () -> Crash.clear(this));
    }

    private void copy(String text) {
        try {
            android.content.ClipboardManager board =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            board.setPrimaryClip(android.content.ClipData.newPlainText("小词窗错误", text));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    /** Deliberately built from literals: the theme, the store or the fonts may be broken. */
    private void showFatal(Throwable error) {
        ready = false;
        try {
            String report = Crash.report(error);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(0xFF14161A);
            final int pad = dp(20);
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                v.setPadding(pad, ins.getSystemWindowInsetTop() + pad, pad,
                        ins.getSystemWindowInsetBottom() + pad);
                return ins;
            });

            TextView title = new TextView(this);
            title.setText("小窗没能启动");
            title.setTextSize(19);
            title.setLetterSpacing(.1f);
            title.setTextColor(0xFFECE7DD);
            title.setTypeface(Ui.serif());
            root.addView(title);

            TextView where = new TextView(this);
            where.setText(Crash.summary(error) + "\n\n把下面这段发给我就能定位。"
                    + "\n也可以从 Android/data/com.kotoba.window/files/last-crash.txt 取出。");
            where.setTextSize(11);
            where.setTextColor(0xFF9C978C);
            where.setPadding(0, dp(10), 0, dp(14));
            where.setTextIsSelectable(true);
            root.addView(where);

            TextView trace = new TextView(this);
            trace.setText(report);
            trace.setTextSize(9.5f);
            trace.setTypeface(android.graphics.Typeface.MONOSPACE);
            trace.setTextColor(0xFFDC7358);
            trace.setTextIsSelectable(true);
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setBackgroundColor(0xFF1B1E24);
            scroll.setPadding(dp(12), dp(12), dp(12), dp(12));
            scroll.addView(trace, new FrameLayout.LayoutParams(-1, -2));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(14), 0, 0);
            TextView copy = new TextView(this);
            copy.setText("复制错误信息");
            copy.setTextSize(14);
            copy.setGravity(Gravity.CENTER);
            copy.setTextColor(0xFF14161A);
            copy.setBackground(Ui.fill(0xFFECE7DD, 12, this));
            copy.setClickable(true);
            copy.setOnClickListener(v -> copy(report));
            actions.addView(copy, new LinearLayout.LayoutParams(0, dp(46), 1f));

            TextView retry = new TextView(this);
            retry.setText("重试");
            retry.setTextSize(14);
            retry.setGravity(Gravity.CENTER);
            retry.setTextColor(0xFFECE7DD);
            retry.setBackground(Ui.box(0x00000000, 12, 0x33ECE7DD, .9f, this));
            retry.setClickable(true);
            retry.setOnClickListener(v -> recreate());
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            rlp.leftMargin = dp(8);
            actions.addView(retry, rlp);
            root.addView(actions);

            setContentView(root);
            root.requestApplyInsets();
        } catch (Throwable second) {
            // Absolute fallback: a plain TextView cannot fail to render.
            TextView plain = new TextView(this);
            plain.setTextColor(0xFFFFFFFF);
            plain.setBackgroundColor(0xFF14161A);
            plain.setTextSize(11);
            plain.setTextIsSelectable(true);
            plain.setText("小窗启动失败\n\n" + Crash.summary(error) + "\n\n" + Crash.report(error));
            setContentView(plain);
        }
    }

    private boolean dark() {
        return !"Light".equals(store.settings().optString("Theme"));
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /* ------------------------------------------------------------------ shell */

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(s.bg);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int bottom = ins.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 30)
                bottom = Math.max(bottom, ins.getInsets(android.view.WindowInsets.Type.ime()).bottom);
            v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                    ins.getSystemWindowInsetRight(), bottom);
            return ins;
        });
        root.addView(header());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(bottomBar());
        setContentView(root);
        root.requestApplyInsets();

        studyPage = new FrameLayout(this);
        study = new StudyView(this, false);
        ((FrameLayout) studyPage).addView(study, new FrameLayout.LayoutParams(-1, -1));
        if (restoreWord != null) {
            study.restore(restoreWord, restoreRevealed, restorePractice);
            restoreWord = null;
        } else {
            study.next();
        }
        show(CARD);
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(20), dp(12), dp(14), dp(10));

        TextView seal = Ui.text(this, "あ", 13, s.inverseInk);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(Ui.fill(s.accent, 6, this));
        bar.addView(seal, new LinearLayout.LayoutParams(dp(26), dp(26)));

        TextView title = Ui.text(this, "小词窗", 20, s.ink);
        title.setTypeface(Ui.serif());
        title.setLetterSpacing(.09f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.leftMargin = dp(10);
        bar.addView(title, tl);

        Ui.IconButton floating = new Ui.IconButton(this, s, Ui.WINDOW, "悬浮").onClick(this::overlay);
        bar.addView(floating, new LinearLayout.LayoutParams(-2, dp(38)));
        return bar;
    }

    private View bottomBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(Ui.hair(this, s.hair));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        navCard = new NavItem(this, Ui.CARD, "背单词", CARD);
        navLibrary = new NavItem(this, Ui.LIST, "词库", LIBRARY);
        navSettings = new NavItem(this, Ui.SLIDERS, "设置", SETTINGS);
        bar.addView(navCard, new LinearLayout.LayoutParams(0, dp(58), 1));
        bar.addView(navLibrary, new LinearLayout.LayoutParams(0, dp(58), 1));
        bar.addView(navSettings, new LinearLayout.LayoutParams(0, dp(58), 1));
        wrap.addView(bar);
        return wrap;
    }

    private final class NavItem extends LinearLayout {
        private final Ui.Ico icon;
        private final TextView label;
        private final View mark;
        private final int target;

        NavItem(Context c, int kind, String text, int target) {
            super(c);
            this.target = target;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            setClickable(true);
            mark = new View(c);
            mark.setBackground(Ui.fill(s.accent, 1, c));
            mark.setAlpha(0f);
            addView(mark, new LinearLayout.LayoutParams(dp(16), dp(2)));
            icon = new Ui.Ico(c, kind, s.muted, 20);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(20), dp(20));
            il.topMargin = dp(10);
            addView(icon, il);
            label = Ui.text(c, text, 10, s.muted);
            label.setLetterSpacing(.14f);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = dp(5);
            addView(label, ll);
            setOnClickListener(v -> show(target));
        }

        void active(boolean on) {
            icon.tint(on ? s.accent : s.muted);
            label.setTextColor(on ? s.accent : s.muted);
            mark.animate().alpha(on ? 1f : 0f).setDuration(170).start();
        }
    }

    private void show(int target) {
        if (content == null) return;
        page = target;
        content.removeAllViews();
        if (target == CARD) {
            study.setActive(true);
            content.addView(studyPage, new FrameLayout.LayoutParams(-1, -1));
        } else {
            study.setActive(false);
            Voice.get(this).stop();
            View incoming = target == LIBRARY ? buildLibrary() : buildSettings();
            content.addView(incoming, new FrameLayout.LayoutParams(-1, -1));
        }
        navCard.active(target == CARD);
        navLibrary.active(target == LIBRARY);
        navSettings.active(target == SETTINGS);
    }

    @Override public void onBackPressed() {
        if (page != CARD) show(CARD);
        else super.onBackPressed();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ready && study != null) {
            if (page == CARD) {
                study.setActive(true);
                study.refresh();
            }
            stopService(new Intent(this, OverlayService.class));
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (study != null) study.setActive(false);
        Voice.get(this).stop();
    }

    /** A theme change needs new colours everywhere, but must not swallow the card. */
    private void rebuildAll() {
        JSONObject current = study == null ? null : study.current();
        boolean wasRevealed = study != null && study.isRevealed();
        boolean freeLook = study != null && study.isPractice();
        int keep = page;
        s = Ui.theme(dark());
        systemBars(dark());
        restoreWord = current;
        restoreRevealed = wasRevealed;
        restorePractice = freeLook;
        build();
        if (keep != CARD) show(keep);
        else study.refresh();
    }

    private void systemBars(boolean dark) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
            w.setNavigationBarContrastEnforced(false);
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            w.setStatusBarColor(s.bg);
            w.setNavigationBarColor(s.bg);
            View decor = w.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(dark ? (flags & ~mask) : (flags | mask));
        }
    }

    private void showLoading() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(s.bg);
        box.setOnApplyWindowInsetsListener((v, ins) -> {
            v.setPadding(ins.getSystemWindowInsetLeft(), ins.getSystemWindowInsetTop(),
                    ins.getSystemWindowInsetRight(), ins.getSystemWindowInsetBottom());
            return ins;
        });

        TextView seal = Ui.text(this, "あ", 15, s.inverseInk);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(Ui.fill(s.accent, 7, this));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(30), dp(30));
        sl.bottomMargin = dp(16);
        box.addView(seal, sl);

        TextView title = Ui.text(this, "小词窗", 26, s.ink);
        title.setTypeface(Ui.serif());
        title.setLetterSpacing(.12f);
        box.addView(title);

        TextView hint = Ui.label(this, "正在载入离线词库", 9.5f, s.muted);
        hint.setLetterSpacing(.2f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = dp(12);
        box.addView(hint, hl);

        setContentView(box);
        box.requestApplyInsets();
    }

    private void showError(String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(s.bg);
        box.setPadding(dp(28), 0, dp(28), 0);
        TextView title = Ui.text(this, "词库载入失败", 20, s.ink);
        title.setTypeface(Ui.serif());
        box.addView(title);
        TextView body = Ui.paragraph(this, message, 13, s.muted);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, dp(12), 0, 0);
        box.addView(body);
        setContentView(box);
    }

    /* --------------------------------------------------------------- settings */

    private View buildSettings() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(s.bg);
        page.addView(pageBar("设置"));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(2), dp(22), dp(34));
        scroll.addView(box, new FrameLayout.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        section(box, "学习", true);
        toggleRow(box, "自动发音", "翻开新卡时朗读读音", "AutoSpeak", true);
        divider(box);
        toggleRow(box, "新词乱序", "按固定种子打乱顺序", "Shuffle", false);
        divider(box);
        toggleRow(box, "翻面前显示假名", "关闭后需要自己回忆读音", "ShowReading", true);

        section(box, "外观", false);
        toggleRow(box, "暗色主题", "纸白或墨黑", "Theme", false);
        divider(box);
        toggleRow(box, "悬浮窗透明背景", "让词卡更像贴在屏幕上的便签", "Transparent", false);
        divider(box);
        opacityRow(box);

        section(box, "每日新词", false);
        quotaRow(box);

        section(box, "词库等级", false);
        levelsRow(box);

        section(box, "进度", false);
        LinearLayout shuffleRow = row("重新洗牌", "换一个顺序重排所有新词");
        shuffleRow.addView(Ui.ghost(this, s, "洗牌", () -> {
            Store.put(store.settings(), "ShuffleSeed", new Random().nextInt());
            Store.put(store.settings(), "Shuffle", true);
            save();
            study.next();
            Toast.makeText(this, "顺序已重排", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(shuffleRow);
        divider(box);
        LinearLayout exportRow = row("导出进度", "生成 JSON 备份文件");
        exportRow.addView(Ui.ghost(this, s, "导出", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .setType("application/json")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, "小词窗进度.json");
            startActivityForResult(i, EXPORT);
        }), new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(exportRow);
        divider(box);
        LinearLayout importRow = row("导入进度", "替换当前进度");
        importRow.addView(Ui.ghost(this, s, "导入", () -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, IMPORT);
        }), new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(importRow);

        section(box, "关于", false);
        TextView about = Ui.paragraph(this,
                "安卓 1.1.2 · N5–N1 共 " + store.words.size() + " 词\n"
                        + "Supertonic 3 离线神经语音，不联网\n"
                        + "悬浮条可拖动、收起，通知栏可回到主界面", 12, s.muted);
        box.addView(about);
        LinearLayout licensesRow = row("来源与许可", "词库 CC BY-NC 4.0 · 模型 OpenRAIL-M");
        licensesRow.addView(Ui.ghost(this, s, "查看", this::licenses), new LinearLayout.LayoutParams(-2, dp(40)));
        box.addView(licensesRow);

        return page;
    }

    private View pageBar(String title) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(16), dp(8));
        bar.addView(new Ui.IconButton(this, s, Ui.BACK, null).onClick(() -> show(CARD)),
                new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView t = Ui.text(this, title, 17, s.ink);
        t.setTypeface(Ui.serif());
        t.setLetterSpacing(.05f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.leftMargin = dp(8);
        bar.addView(t, tl);
        wrap.addView(bar);
        wrap.addView(Ui.hair(this, s.hair));
        return wrap;
    }

    private void section(LinearLayout box, String name, boolean first) {
        if (!first) {
            View gap = new View(this);
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, dp(1));
            gl.topMargin = dp(26);
            box.addView(gap, gl);
            box.addView(Ui.hair(this, s.hair));
        }
        TextView label = Ui.label(this, name, 9.5f, s.accent);
        label.setLetterSpacing(.22f);
        label.setPadding(0, dp(first ? 16 : 12), 0, dp(4));
        box.addView(label);
    }

    private LinearLayout row(String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(52));
        r.setPadding(0, dp(8), 0, dp(8));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(this, title, 14, s.ink));
        if (sub != null) {
            TextView st = Ui.text(this, sub, 11.5f, s.muted);
            st.setPadding(0, dp(3), dp(10), 0);
            col.addView(st);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        return r;
    }

    private void divider(LinearLayout box) {
        box.addView(Ui.hair(this, s.hair));
    }

    private void toggleRow(LinearLayout box, String title, String sub, final String key, boolean fallback) {
        // Theme is stored as the string "Dark"/"Light", so optBoolean would read it as false
        // and the switch would start out inverted.
        boolean initial = key.equals("Theme")
                ? !"Light".equals(store.settings().optString("Theme", "Dark"))
                : store.settings().optBoolean(key, fallback);
        final Ui.Toggle toggle = new Ui.Toggle(this, s, initial);
        toggle.setOnChange(() -> {
            if (key.equals("Theme")) {
                Store.put(store.settings(), "Theme", toggle.isOn() ? "Dark" : "Light");
                save();
                rebuildAll();
                return;
            }
            Store.put(store.settings(), key, toggle.isOn());
            if (key.equals("AutoSpeak") && !toggle.isOn()) Voice.get(this).stop();
            save();
        });
        LinearLayout r = row(title, sub);
        r.addView(toggle, new LinearLayout.LayoutParams(dp(46), dp(26)));
        box.addView(r);
    }

    private void opacityRow(LinearLayout box) {
        double stored = store.settings().optDouble("BackgroundOpacity", .8);
        final TextView value = Ui.text(this, Math.round(stored * 100) + "%", 12.5f, s.muted);
        value.setLetterSpacing(.04f);
        LinearLayout r = row("悬浮背景浓度", "影响透明模式下的纸面厚度");
        r.addView(value);
        box.addView(r);

        final Ui.Slider slider = new Ui.Slider(this, s, (float) ((stored - .2) / .8));
        slider.setOnChange(() -> {
            double opacity = .2 + .8 * slider.value();
            value.setText(Math.round(opacity * 100) + "%");
            Store.put(store.settings(), "BackgroundOpacity", opacity);
            save();
        });
        box.addView(slider);
    }

    private void quotaRow(LinearLayout box) {
        final int[] quota = {store.settings().optInt("DailyNew", 20)};
        final TextView value = Ui.text(this, String.valueOf(quota[0]), 15, s.ink);
        value.setTypeface(Ui.serif());
        value.setGravity(Gravity.CENTER);

        Ui.IconButton minus = new Ui.IconButton(this, s, Ui.MINUS, null).onClick(() -> {
            quota[0] = Math.max(0, quota[0] - 5);
            Store.put(store.settings(), "DailyNew", quota[0]);
            value.setText(String.valueOf(quota[0]));
            save();
        });
        Ui.IconButton plus = new Ui.IconButton(this, s, Ui.PLUS, null).onClick(() -> {
            quota[0] = Math.min(1000, quota[0] + 5);
            Store.put(store.settings(), "DailyNew", quota[0]);
            value.setText(String.valueOf(quota[0]));
            save();
        });

        LinearLayout stepper = new LinearLayout(this);
        stepper.setOrientation(LinearLayout.HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(34), dp(34)));
        stepper.addView(value, new LinearLayout.LayoutParams(dp(46), dp(34)));
        stepper.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout r = row("每日新词额度", "所有等级共享，步长 5");
        r.addView(stepper);
        box.addView(r);
    }

    private void levelsRow(LinearLayout box) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(10), 0, dp(6));
        for (int l = 5; l >= 1; l--) {
            final int level = l;
            final Ui.Chip chip = new Ui.Chip(this, s, "N" + l, store.includes(l));
            chip.setPadding(dp(4), 0, dp(4), 0);
            chip.setOnToggle(() -> {
                if (!chip.isOn()) {
                    int chosen = 0;
                    for (int k = 1; k <= 5; k++) if (k != level && store.includes(k)) chosen++;
                    if (chosen == 0) {
                        chip.setOn(true);
                        Toast.makeText(this, "至少保留一个等级", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                JSONArray levels = new JSONArray();
                for (int k = 5; k >= 1; k--) {
                    boolean on = k == level ? chip.isOn() : store.includes(k);
                    if (on) levels.put(k);
                }
                Store.put(store.settings(), "Levels", levels);
                Store.put(store.settings(), "Level", levels.optInt(0));
                save();
                study.next();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            r.addView(chip, lp);
        }
        box.addView(r);
    }

    /* ---------------------------------------------------------------- library */

    private View buildLibrary() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(s.bg);
        page.addView(pageBar("词库"));

        LinearLayout search = new LinearLayout(this);
        search.setOrientation(LinearLayout.HORIZONTAL);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setBackground(Ui.box(s.surface, 12, s.line, .8f, this));
        search.setPadding(dp(12), 0, dp(12), 0);
        search.addView(new Ui.Ico(this, Ui.SEARCH, s.faint, 16));
        final EditText field = new EditText(this);
        field.setHint("搜索日语、假名或中文");
        field.setText(libraryQuery);
        field.setTextSize(14);
        field.setTextColor(s.ink);
        field.setHintTextColor(s.faint);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setHighlightColor(Ui.withAlpha(s.accent, 60));
        field.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, dp(46), 1);
        fl.leftMargin = dp(10);
        search.addView(field, fl);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, dp(46));
        sl.leftMargin = dp(20);
        sl.rightMargin = dp(20);
        sl.topMargin = dp(12);
        page.addView(search, sl);

        final TextView count = Ui.text(this, "", 10, s.muted);
        count.setLetterSpacing(.12f);
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(dp(20), dp(12), dp(20), dp(8));
        final Ui.Chip starred = new Ui.Chip(this, s, "只看收藏", false);
        filters.addView(starred, new LinearLayout.LayoutParams(-2, dp(32)));
        filters.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        count.setGravity(Gravity.END);
        page.addView(filters);

        final ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(s.hair));
        list.setDividerHeight(Math.max(1, dp(.8f)));
        list.setSelector(new ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        final LibraryAdapter adapter = new LibraryAdapter();
        list.setAdapter(adapter);

        final Runnable apply = () -> {
            adapter.filter(field.getText().toString().trim(), starred.isOn());
            int n = adapter.getCount();
            count.setText(n == 0 ? "没有匹配的词" : (n >= 200 ? "匹配 200+" : "匹配 " + n));
        };
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            public void onTextChanged(CharSequence c, int a, int b, int d) { apply.run(); }
            public void afterTextChanged(Editable e) {}
        });
        starred.setOnToggle(apply);
        list.setOnItemClickListener((AdapterView<?> parent, View v, int pos, long id) -> {
            libraryQuery = field.getText().toString();
            study.showWord(adapter.items.get(pos));
            show(CARD);
        });
        apply.run();
        return page;
    }

    private final class LibraryAdapter extends BaseAdapter {
        final ArrayList<JSONObject> items = new ArrayList<>();

        void filter(String query, boolean starredOnly) {
            items.clear();
            String q = query == null ? "" : query;
            for (JSONObject w : store.words) {
                if (!store.includes(w.optInt("Level"))) continue;
                JSONObject r = store.progress(w);
                if (starredOnly && (r == null || !r.optBoolean("Star"))) continue;
                if (!q.isEmpty()
                        && !(w.optString("Term") + w.optString("Reading") + w.optString("Meaning")).contains(q))
                    continue;
                items.add(w);
                if (items.size() == 200) break;
            }
            notifyDataSetChanged();
        }

        public int getCount() { return items.size(); }

        public Object getItem(int i) { return items.get(i); }

        public long getItemId(int i) { return i; }

        public View getView(int pos, View reused, ViewGroup parent) {
            LibRow row = reused instanceof LibRow ? (LibRow) reused : new LibRow();
            JSONObject w = items.get(pos);
            JSONObject r = store.progress(w);
            row.term.setText(w.optString("Term"));
            row.reading.setText(w.optString("Reading"));
            row.meaning.setText(w.optString("Meaning"));
            row.level.setText("N" + w.optInt("Level"));
            row.star.setVisibility(r != null && r.optBoolean("Star") ? View.VISIBLE : View.GONE);
            return row;
        }
    }

    private final class LibRow extends LinearLayout {
        final TextView term, reading, meaning, level;
        final Ui.Ico star;

        LibRow() {
            super(MainActivity.this);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(20), dp(11), dp(18), dp(11));
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(VERTICAL);
            term = Ui.text(MainActivity.this, "", 19, s.ink);
            term.setTypeface(Ui.serif());
            term.setSingleLine(true);
            col.addView(term);
            reading = Ui.text(MainActivity.this, "", 11.5f, s.muted);
            reading.setLetterSpacing(.06f);
            reading.setPadding(0, dp(2), 0, 0);
            col.addView(reading);
            meaning = Ui.text(MainActivity.this, "", 12.5f, s.muted);
            meaning.setMaxLines(2);
            meaning.setEllipsize(TextUtils.TruncateAt.END);
            meaning.setPadding(0, dp(4), 0, 0);
            col.addView(meaning);
            addView(col, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout right = new LinearLayout(MainActivity.this);
            right.setOrientation(VERTICAL);
            right.setGravity(Gravity.CENTER_HORIZONTAL);
            star = new Ui.Ico(MainActivity.this, Ui.STAR_ON, s.accent, 12);
            right.addView(star, new LinearLayout.LayoutParams(dp(12), dp(12)));
            level = Ui.text(MainActivity.this, "", 9.5f, s.accent);
            level.setGravity(Gravity.CENTER);
            level.setLetterSpacing(.06f);
            level.setBackground(Ui.box(0x00000000, 5, Ui.withAlpha(s.accent, 110), .9f, MainActivity.this));
            level.setPadding(dp(5), dp(2), dp(5), dp(2));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = dp(7);
            right.addView(level, ll);
            addView(right);
        }
    }

    /* --------------------------------------------------------------- printing */

    private void save() {
        if (!store.save()) Toast.makeText(this, store.warning, Toast.LENGTH_LONG).show();
    }

    private void overlay() {
        if (!Settings.canDrawOverlays(this)) {
            Sheet.show(this, s, "让词卡浮在上面",
                    "需要在系统设置里允许「小词窗」显示在其他应用上层。授权后回到这里再点一次悬浮。",
                    false, "去授权", () -> startActivity(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()))),
                    "取消", null);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 22);
        }
        startForegroundService(new Intent(this, OverlayService.class));
        moveTaskToBack(true);
    }

    private void licenses() {
        try (InputStream in = getAssets().open("VOCAB_SOURCE.md")) {
            String source = Store.read(in);
            Sheet.show(this, s, "来源与许可",
                    source + "\n应用代码 MIT；Supertonic 模型 OpenRAIL-M；"
                            + "sherpa-onnx Apache-2.0；ONNX Runtime MIT。\n完整许可文本随 APK 附带。",
                    true, "好", null, null, null);
        } catch (Exception e) {
            Sheet.note(this, s, "来源与许可", "无法读取许可文件。");
        }
    }

    @Override protected void onActivityResult(int req, int result, Intent intent) {
        super.onActivityResult(req, result, intent);
        if (result != RESULT_OK || intent == null || intent.getData() == null) return;
        try {
            if (req == EXPORT) {
                try (OutputStream out = getContentResolver().openOutputStream(intent.getData())) {
                    out.write(store.data.toString().getBytes("UTF-8"));
                }
                Toast.makeText(this, "进度已导出", Toast.LENGTH_SHORT).show();
            } else if (req == IMPORT) {
                JSONObject candidate;
                try (InputStream in = getContentResolver().openInputStream(intent.getData())) {
                    candidate = new JSONObject(Store.read(in));
                }
                Store.validate(candidate);
                Sheet.show(this, s, "替换当前学习进度？",
                        "这一操作会覆盖现有的复习记录，建议先导出一份备份。",
                        false, "导入", () -> {
                            JSONObject old = store.data;
                            store.data = candidate;
                            if (store.save()) {
                                study.next();
                                Toast.makeText(this, "进度已导入", Toast.LENGTH_SHORT).show();
                            } else {
                                store.data = old;
                                Toast.makeText(this, store.warning, Toast.LENGTH_LONG).show();
                            }
                        }, "取消", null);
            }
        } catch (Exception e) {
            Toast.makeText(this, "操作失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
