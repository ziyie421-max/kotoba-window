package com.kotoba.window;

import android.content.Context;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.util.ArrayList;

/**
 * The flashcard itself. The skeleton is built once and only text, colours and
 * enabled states change afterwards, so revealing an answer neither flashes the
 * screen nor resets scroll position.
 */
final class StudyView extends LinearLayout {

    final Store store;
    final boolean floating;
    final Ui.Scheme s;

    JSONObject word, undo;
    String undoId;
    boolean revealed, practice, active = true;
    Runnable pendingSpeech, tick;

    private TextView meta, tag, term, reading, meaning, note, status, undoBtn, hint, seal;
    private TextView cta, exampleText;
    private Ui.IconButton listen, fav;
    private Ui.Ico exampleIco;
    private LinearLayout exampleBlock, examplePanel;
    private FrameLayout slot;
    private RatingCell[] cells = new RatingCell[4];
    private String lastTerm = "";
    private int lastMeaningColor;

    StudyView(Context c, boolean f) {
        super(c);
        floating = f;
        store = Store.get(c);
        s = Ui.theme(dark());
        setOrientation(VERTICAL);
        setPadding(dp(floating ? 13 : 24), dp(floating ? 10 : 18),
                dp(floating ? 13 : 24), dp(floating ? 10 : 18));
        build();
        tick = () -> {
            if (word == null) next();
            postDelayed(tick, 15000);
        };
        postDelayed(tick, 15000);
    }

    int dp(float n) {
        return Ui.dp(getContext(), n);
    }

    boolean dark() {
        return !"Light".equals(store.settings().optString("Theme"));
    }

    boolean quiet() {
        return floating && store.settings().optBoolean("Transparent");
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        active = true;
        // Switching tabs detaches the card; the watchdog has to be re-armed.
        if (tick != null) {
            removeCallbacks(tick);
            postDelayed(tick, 15000);
        }
    }

    void setActive(boolean a) {
        active = a;
        if (!a && pendingSpeech != null) removeCallbacks(pendingSpeech);
    }

    /* --------------------------------------------------------------- skeleton */

    private void build() {
        // Floating mode is painted by the overlay window, so the card has one outline,
        // not a card inside a card.
        if (!floating) setBackgroundColor(s.bg);

        meta = Ui.text(getContext(), "", 10f, s.muted);
        meta.setLetterSpacing(.1f);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        addView(meta, new LinearLayout.LayoutParams(-1, -2));
        addView(Ui.hair(getContext(), s.hair));

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(VERTICAL);
        // CENTER (not just horizontal): with fillViewport the body is stretched to the
        // viewport, so the card sits in the optical middle instead of hugging the top.
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(2), dp(floating ? 14 : 22), dp(2), dp(floating ? 12 : 20));
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(getContext());
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        tag = Ui.label(getContext(), "", 10f, s.accent);
        head.addView(tag, new LinearLayout.LayoutParams(0, -2, 1));
        seal = Ui.text(getContext(), "", 9.5f, s.accent);
        seal.setLetterSpacing(.06f);
        seal.setGravity(Gravity.CENTER);
        seal.setBackground(Ui.box(0x00000000, 5, Ui.withAlpha(s.accent, 120), .9f, getContext()));
        seal.setPadding(dp(6), dp(3), dp(6), dp(3));
        head.addView(seal);
        body.addView(head, new LinearLayout.LayoutParams(-1, -2));

        term = Ui.text(getContext(), "", floating ? 32 : 46, s.ink);
        term.setTypeface(Ui.serif());
        term.setGravity(Gravity.CENTER);
        term.setLetterSpacing(.02f);
        term.setLineSpacing(dp(6), 1f);
        term.setPadding(0, dp(floating ? 10 : 14), 0, 0);
        body.addView(term, new LinearLayout.LayoutParams(-1, -2));

        reading = Ui.text(getContext(), "", floating ? 14 : 16, s.muted);
        reading.setGravity(Gravity.CENTER);
        reading.setLetterSpacing(.08f);
        reading.setPadding(0, dp(6), 0, 0);
        body.addView(reading, new LinearLayout.LayoutParams(-1, -2));

        View dash = new View(getContext());
        dash.setBackground(Ui.fill(Ui.withAlpha(s.accent, 150), 1, getContext()));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(18), dp(2));
        dlp.topMargin = dp(floating ? 12 : 16);
        dlp.bottomMargin = dp(floating ? 8 : 12);
        body.addView(dash, dlp);

        meaning = Ui.paragraph(getContext(), "", floating ? 15 : 17, s.ink);
        meaning.setGravity(Gravity.CENTER);
        body.addView(meaning, new LinearLayout.LayoutParams(-1, -2));

        note = Ui.paragraph(getContext(), "", floating ? 12 : 13, s.muted);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(8), 0, 0);
        body.addView(note, new LinearLayout.LayoutParams(-1, -2));

        exampleBlock = new LinearLayout(getContext());
        exampleBlock.setOrientation(VERTICAL);
        exampleBlock.setPadding(0, dp(floating ? 10 : 14), 0, 0);

        LinearLayout toggleRow = new LinearLayout(getContext());
        toggleRow.setOrientation(HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER);
        TextView exampleToggle = Ui.label(getContext(), "例句", 10f, s.muted);
        toggleRow.addView(exampleToggle);
        exampleIco = new Ui.Ico(getContext(), Ui.CHEVRON, s.muted, 11);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(11), dp(11));
        ilp.leftMargin = dp(4);
        toggleRow.addView(exampleIco, ilp);
        toggleRow.setClickable(true);
        toggleRow.setOnClickListener(v -> toggleExample());
        exampleBlock.addView(toggleRow, new LinearLayout.LayoutParams(-1, dp(28)));

        examplePanel = new LinearLayout(getContext());
        examplePanel.setOrientation(HORIZONTAL);
        examplePanel.setVisibility(GONE);
        View bar = new View(getContext());
        bar.setBackground(Ui.fill(Ui.withAlpha(s.accent, 110), 1, getContext()));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(2), -1);
        blp.rightMargin = dp(10);
        blp.topMargin = dp(3);
        blp.bottomMargin = dp(3);
        examplePanel.addView(bar, blp);
        exampleText = Ui.paragraph(getContext(), "", floating ? 12.5f : 14, s.muted);
        exampleText.setLineSpacing(dp(4), 1f);
        examplePanel.addView(exampleText, new LinearLayout.LayoutParams(0, -2, 1));
        exampleBlock.addView(examplePanel, new LinearLayout.LayoutParams(-1, -2));

        body.addView(exampleBlock, new LinearLayout.LayoutParams(-1, -2));
        addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(Ui.hair(getContext(), s.hair));

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        actions.setPadding(0, dp(floating ? 9 : 12), 0, 0);
        listen = new Ui.IconButton(getContext(), s, Ui.SPEAKER, "听发音");
        listen.onClick(this::speak);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp1.rightMargin = dp(4);
        actions.addView(listen, lp1);
        fav = new Ui.IconButton(getContext(), s, Ui.STAR, "收藏");
        fav.onClick(this::favorite);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp2.leftMargin = dp(4);
        actions.addView(fav, lp2);
        addView(actions);

        slot = new FrameLayout(getContext());
        slot.setPadding(0, dp(8), 0, 0);
        cta = Ui.solid(getContext(), s, "显示答案", this::primary);
        slot.addView(cta, new FrameLayout.LayoutParams(-1, dp(48)));
        hint = Ui.text(getContext(), "", 11.5f, s.muted);
        hint.setGravity(Gravity.CENTER);
        hint.setLetterSpacing(.06f);
        hint.setVisibility(GONE);
        slot.addView(hint, new FrameLayout.LayoutParams(-1, dp(48)));
        addView(slot, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout ratings = new LinearLayout(getContext());
        ratings.setOrientation(HORIZONTAL);
        ratings.setBackground(Ui.box(0x00000000, 12, s.line, .9f, getContext()));
        ratings.setClipToOutline(true);
        String[] names = {"忘记", "困难", "记得", "轻松"};
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                View div = new View(getContext());
                div.setBackgroundColor(s.line);
                ratings.addView(div, new LinearLayout.LayoutParams(Math.max(1, dp(.9f)), -1));
            }
            cells[i] = new RatingCell(getContext(), i + 1, names[i]);
            ratings.addView(cells[i], new LinearLayout.LayoutParams(0, -1, 1));
        }
        addView(ratings, new LinearLayout.LayoutParams(-1, dp(floating ? 54 : 58)));

        undoBtn = Ui.text(getContext(), "↶  撤销上一次评分", 11.5f, s.muted);
        undoBtn.setGravity(Gravity.CENTER);
        undoBtn.setLetterSpacing(.04f);
        undoBtn.setBackground(Ui.states(Ui.fill(0x00000000, 8, getContext()),
                Ui.fill(s.accentSoft, 8, getContext()), Ui.fill(0x00000000, 8, getContext())));
        undoBtn.setClickable(true);
        undoBtn.setOnClickListener(v -> undo());
        addView(undoBtn, new LinearLayout.LayoutParams(-1, dp(34)));

        status = Ui.text(getContext(), "", 10f, s.muted);
        status.setGravity(Gravity.CENTER);
        status.setLetterSpacing(.04f);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        addView(status, new LinearLayout.LayoutParams(-1, -2));
    }

    /** One quarter of the rating scale: name on top, next interval underneath. */
    private final class RatingCell extends LinearLayout {
        final int score;
        final TextView name, preview;

        RatingCell(Context c, int score, String text) {
            super(c);
            this.score = score;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            name = Ui.text(c, text, 12.5f, s.ink);
            name.setGravity(Gravity.CENTER);
            preview = Ui.text(c, "", 9.5f, s.muted);
            preview.setGravity(Gravity.CENTER);
            preview.setPadding(0, dp(3), 0, 0);
            addView(name);
            addView(preview);
            setBackground(Ui.states(Ui.fill(0x00000000, 0, getContext()),
                    Ui.fill(s.accentSoft, 0, getContext()), Ui.fill(0x00000000, 0, getContext())));
            setOnClickListener(v -> grade(score));
            setOnTouchListener((v, e) -> {
                boolean live = v.isEnabled();
                if (e.getAction() == MotionEvent.ACTION_DOWN && live) {
                    name.setTextColor(s.accent);
                    preview.setTextColor(s.accent);
                } else {
                    name.setTextColor(live ? s.ink : s.faint);
                    preview.setTextColor(live ? s.muted : s.faint);
                }
                return false;
            });
        }

        void setLive(boolean live, String interval) {
            setEnabled(live);
            setClickable(live);
            name.setTextColor(live ? s.ink : s.faint);
            preview.setTextColor(live ? s.muted : s.faint);
            preview.setText(interval);
        }
    }

    /* ------------------------------------------------------------- card state */

    void next() {
        Voice.get(getContext()).stop();
        ArrayList<JSONObject> q = store.queue();
        word = q.isEmpty() ? null : q.get(0);
        revealed = false;
        practice = false;
        apply();
        if (word != null && store.settings().optBoolean("AutoSpeak", true)) {
            if (pendingSpeech != null) removeCallbacks(pendingSpeech);
            pendingSpeech = () -> {
                PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
                if (active && isShown() && pm.isInteractive()) speak();
            };
            postDelayed(pendingSpeech, 350);
        }
    }

    void refresh() {
        if (word == null) next();
        else apply();
    }

    JSONObject current() {
        return word;
    }

    boolean isRevealed() {
        return revealed;
    }

    boolean isPractice() {
        return practice;
    }

    void restore(JSONObject w, boolean wasRevealed, boolean freeLook) {
        if (w == null) {
            next();
            return;
        }
        word = w;
        revealed = wasRevealed;
        practice = freeLook;
        apply();
    }

    void showWord(JSONObject w) {
        Voice.get(getContext()).stop();
        word = w;
        practice = true;
        revealed = true;
        apply();
        if (store.settings().optBoolean("AutoSpeak", true)) speak();
    }

    private void apply() {
        JSONObject r = store.progress(word);
        boolean blank = word == null;
        boolean fresh = r == null || r.optInt("Reviews") == 0;
        boolean shown = revealed || practice || blank;

        meta.setText(metaLine());
        tag.setText(blank ? "一轮结束" : practice ? "自由查看" : fresh ? "新词" : "复习");
        tag.setTextColor(practice ? s.muted : blank || fresh ? s.accent : s.muted);
        seal.setVisibility(blank ? GONE : VISIBLE);
        if (!blank) seal.setText("N" + word.optInt("Level"));

        String headword = blank ? "这一轮完成了" : word.optString("Term");
        if (!headword.equals(lastTerm)) {
            term.setText(headword);
            term.setTextSize(fit(headword));
            lastTerm = headword;
            steer(term);
        } else {
            term.setText(headword);
            term.setTextSize(fit(headword));
        }

        String read = blank ? "" : word.optString("Reading");
        reading.setText(read);
        reading.setVisibility(TextUtils.isEmpty(read) ? GONE : VISIBLE);

        if (blank) {
            meaning.setText("到期复习会自己回到队列，\n明天的额度按设置重新开始。");
            note.setText("可以在设置里调整每日新词额度");
        } else if (shown) {
            meaning.setText(word.optString("Meaning"));
            note.setText(word.optString("Note"));
        } else {
            meaning.setText("先回忆读音和意思");
            note.setText("");
        }
        int color = shown && !blank ? s.ink : s.muted;
        if (color != lastMeaningColor) {
            meaning.setTextColor(color);
            lastMeaningColor = color;
        }
        note.setVisibility(TextUtils.isEmpty(note.getText()) ? GONE : VISIBLE);
        note.setTextColor(s.muted);

        String example = blank ? "" : word.optString("Example");
        boolean hasExample = !TextUtils.isEmpty(example);
        exampleBlock.setVisibility(hasExample ? VISIBLE : GONE);
        if (hasExample) {
            exampleText.setText(example + "\n" + word.optString("ExampleMeaning"));
            if (!shown) {
                examplePanel.setVisibility(GONE);
                exampleIco.setRotation(0);
            }
        }

        boolean live = !blank && revealed && !practice;
        for (int i = 0; i < 4; i++) {
            // The interval is a property of the schedule, not of the answer, so it is
            // readable before the card is turned over; only grading waits for the reveal.
            cells[i].setLive(live, blank ? "—" : Store.preview(r, i + 1));
        }

        undoBtn.setEnabled(undo != null);
        undoBtn.setTextColor(undo != null ? s.muted : s.faint);
        undoBtn.setAlpha(undo != null ? 1f : .6f);

        listen.dim(blank);
        boolean starred = r != null && r.optBoolean("Star");
        fav.dim(blank);
        if (!blank) fav.kind(starred ? Ui.STAR_ON : Ui.STAR).accent(starred).text(starred ? "已收藏" : "收藏");
        else fav.text("收藏");

        if (practice) {
            cta.setText("回到复习");
            cta.setVisibility(VISIBLE);
            hint.setVisibility(GONE);
        } else if (blank) {
            cta.setText("刷新卡片");
            cta.setVisibility(VISIBLE);
            hint.setVisibility(GONE);
        } else if (revealed) {
            cta.setVisibility(GONE);
            hint.setText("选择记忆程度，决定下次出现的时间");
            hint.setVisibility(VISIBLE);
        } else {
            cta.setText("显示答案");
            cta.setVisibility(VISIBLE);
            hint.setVisibility(GONE);
        }

        status.setText(store.warning.isEmpty()
                ? (floating ? "离线词库 · Supertonic 3" : "离线词库 · Supertonic 3 神经语音")
                : store.warning);
    }

    /** Long terms drop a size so they never break into a wall of characters. */
    private float fit(String text) {
        int n = text.codePointCount(0, text.length());
        if (floating) return n <= 3 ? 32 : n <= 5 ? 27 : n <= 8 ? 22 : 18;
        return n <= 3 ? 46 : n <= 5 ? 38 : n <= 8 ? 30 : 24;
    }

    /** A 4dp lift and a fade: enough to signal a new card, quiet enough to repeat 200 times. */
    private void steer(final TextView v) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dp(5));
        v.animate().alpha(1f).translationY(0f).setDuration(190).start();
    }

    /* ---------------------------------------------------------------- actions */

    private void primary() {
        if (word == null || practice) {
            next();
            return;
        }
        if (revealed) return;
        revealed = true;
        apply();
        steer(meaning);
    }

    private void grade(int g) {
        if (word == null || !revealed || practice) return;
        JSONObject before = cloneData();
        store.grade(word, g);
        if (store.save()) {
            undo = before;
            undoId = word.optString("Id");
            next();
        } else {
            store.data = before;
            apply();
        }
    }

    private void undo() {
        if (undo == null) return;
        JSONObject before = store.data;
        Store.put(undo, "Settings", before.optJSONObject("Settings"));
        store.data = undo;
        if (!store.save()) {
            store.data = before;
            return;
        }
        word = null;
        for (JSONObject w : store.words) if (w.optString("Id").equals(undoId)) {
            word = w;
            break;
        }
        undo = null;
        revealed = true;
        practice = false;
        Voice.get(getContext()).stop();
        apply();
    }

    private void favorite() {
        if (word == null) return;
        JSONObject before = cloneData(), r = store.progress(word);
        if (r == null) {
            try {
                r = new JSONObject();
            } catch (Exception e) {
                return;
            }
            Store.put(r, "Id", word.optString("Id"));
            Store.put(store.progress(), word.optString("Id"), r);
        }
        Store.put(r, "Star", !r.optBoolean("Star"));
        if (!store.save()) store.data = before;
        undo = null;
        apply();
    }

    void speak() {
        if (!active || word == null) return;
        Voice.get(getContext()).speak(word.optString("Reading", word.optString("Term")),
                word.optString("Id"), text -> {
                    if (isAttachedToWindow() && status != null) status.setText(text);
                });
    }

    private void toggleExample() {
        boolean open = examplePanel.getVisibility() != VISIBLE;
        examplePanel.setVisibility(open ? VISIBLE : GONE);
        exampleIco.animate().rotation(open ? 90 : 0).setDuration(150).start();
        if (open) {
            examplePanel.setAlpha(0f);
            examplePanel.animate().alpha(1f).setDuration(170).start();
        }
    }

    JSONObject cloneData() {
        try {
            return new JSONObject(store.data.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override protected void onDetachedFromWindow() {
        active = false;
        if (pendingSpeech != null) removeCallbacks(pendingSpeech);
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /* ------------------------------------------------------------------- meta */

    private String metaLine() {
        return levels() + "  ·  今日 " + store.count("DailyReviews")
                + "  ·  待复习 " + store.due()
                + "  ·  新词 " + Math.min(store.count("DailyNew"), store.settings().optInt("DailyNew", 20))
                + "/" + store.settings().optInt("DailyNew", 20);
    }

    /** "N4–N5" reads faster than "N5 + N4" and keeps the meta line short. */
    private String levels() {
        ArrayList<Integer> on = new ArrayList<>();
        for (int l = 5; l >= 1; l--) if (store.includes(l)) on.add(l);
        if (on.isEmpty()) return "—";
        boolean contiguous = true;
        for (int i = 1; i < on.size(); i++) if (on.get(i - 1) - on.get(i) != 1) contiguous = false;
        if (on.size() > 1 && contiguous)
            return "N" + on.get(on.size() - 1) + "–N" + on.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < on.size(); i++) {
            if (i > 0) sb.append('·');
            sb.append('N').append(on.get(i));
        }
        return sb.toString();
    }
}
