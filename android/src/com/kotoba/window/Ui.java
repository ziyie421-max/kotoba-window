package com.kotoba.window;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Design tokens plus the few primitives the screens share.
 *
 * The look is warm paper and ink: a printed-card feel rather than a dashboard of
 * rounded boxes. Vermilion (#B0452C / #E0806A) is the only chromatic accent and it
 * stays small: the new-word label, the star, the level seal, the slider thumb.
 */
final class Ui {

    private Ui() {}

    /* ---------------------------------------------------------------- tokens */

    static final class Scheme {
        final boolean dark;
        final int bg, surface, ink, muted, faint, line, hair, accent, accentSoft, inverseBg, inverseInk;

        Scheme(boolean dark, int bg, int surface, int ink, int muted, int faint,
               int line, int hair, int accent, int accentSoft, int inverseBg, int inverseInk) {
            this.dark = dark;
            this.bg = bg;
            this.surface = surface;
            this.ink = ink;
            this.muted = muted;
            this.faint = faint;
            this.line = line;
            this.hair = hair;
            this.accent = accent;
            this.accentSoft = accentSoft;
            this.inverseBg = inverseBg;
            this.inverseInk = inverseInk;
        }
    }

    static Scheme theme(boolean dark) {
        if (dark) {
            return new Scheme(true,
                    0xFF14161A, // page, ink-washed charcoal
                    0xFF1B1E24, // raised surface
                    0xFFECE7DD, // paper text
                    0xFF9C978C, // secondary
                    0xFF6A655D, // disabled / decorative only
                    0x2EECE7DD, // hairline
                    0x17ECE7DD, // faintest hairline
                    0xFFDC7358, // vermilion, lifted for dark
                    0xFF3B2722, // vermilion wash
                    0xFFECE7DD,
                    0xFF14161A);
        }
        return new Scheme(false,
                0xFFFAF6EF,
                0xFFFFFDF8,
                0xFF1E2024,
                0xFF6B675F,
                0xFF9C968B,
                0x2A1E2024,
                0x141E2024,
                0xFFB0452C,
                0xFFF5E5DA,
                0xFF1E2024,
                0xFFFAF6EF);
    }

    static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    static int dp(Context c, float v) {
        return (int) (v * density(c) + .5f);
    }

    /** Scaled pixels, for values that go into setLineSpacing rather than setTextSize. */
    static float sp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().scaledDensity;
    }

    static Typeface serif() {
        return Typeface.create("serif", Typeface.NORMAL);
    }

    /* -------------------------------------------------------------- surfaces */

    static GradientDrawable fill(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable box(int color, float radiusDp, int strokeColor, float strokeDp, Context c) {
        GradientDrawable d = fill(color, radiusDp, c);
        if (strokeColor != 0) d.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return d;
    }

    /** normal / pressed / disabled in one drawable, so no touch listeners are needed. */
    static Drawable states(Drawable normal, Drawable pressed, Drawable disabled) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, disabled);
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    static ColorStateList colors(int enabled, int disabled) {
        return new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{disabled, enabled});
    }

    /* ------------------------------------------------------------------ text */

    static TextView text(Context c, CharSequence s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Body copy: looser leading, since meaning and examples are read, not scanned. */
    static TextView paragraph(Context c, CharSequence s, float size, int color) {
        TextView t = text(c, s, size, color);
        t.setLineSpacing(sp(c, 3.5f), 1f);
        return t;
    }

    /** Small caps label: the workhorse of this interface's hierarchy. */
    static TextView label(Context c, CharSequence s, float size, int color) {
        TextView t = text(c, s, size, color);
        t.setLetterSpacing(.16f);
        t.setAllCaps(true);
        return t;
    }

    static View hair(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, .8f))));
        return v;
    }

    /** Squash slightly on touch. Cheap, and it makes flat buttons feel physical. */
    static void press(final View v) {
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) view.animate().scaleX(.975f).scaleY(.975f).setDuration(80).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            return false;
        });
    }

    /* --------------------------------------------------------------- buttons */

    /** Ghost button: hairline outline, transparent ground. */
    static TextView ghost(Context c, Scheme s, CharSequence label, Runnable action) {
        TextView t = text(c, label, 12.5f, s.ink);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(.04f);
        t.setMinHeight(dp(c, 40));
        t.setBackground(states(
                box(0x00000000, 10, s.line, .8f, c),
                box(s.accentSoft, 10, s.accent, .8f, c),
                box(0x00000000, 10, s.hair, .8f, c)));
        t.setTextColor(colors(s.ink, s.faint));
        t.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        t.setClickable(true);
        t.setOnClickListener(v -> action.run());
        press(t);
        return t;
    }

    /** Icon plus optional label, hairline framed. Used for card actions and overlay chrome. */
    static final class IconButton extends LinearLayout {
        final Ico icon;
        final TextView label;
        private final Scheme scheme;

        IconButton(Context c, Scheme s, int kind, String text) {
            super(c);
            scheme = s;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(dp(c, text == null ? 5 : 10), 0, dp(c, text == null ? 5 : 12), 0);
            setBackground(states(
                    box(0x00000000, 10, s.line, .8f, c),
                    box(s.accentSoft, 10, s.accent, .8f, c),
                    box(0x00000000, 10, s.hair, .8f, c)));
            setClickable(true);
            icon = new Ico(c, kind, s.ink, 15);
            addView(icon);
            if (text == null) {
                label = null;
            } else {
                label = Ui.text(c, text, 12.5f, s.ink);
                label.setLetterSpacing(.04f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = dp(c, 6);
                addView(label, lp);
            }
            press(this);
        }

        IconButton onClick(Runnable r) {
            setOnClickListener(v -> r.run());
            return this;
        }

        IconButton accent(boolean on) {
            icon.tint(on ? scheme.accent : scheme.ink);
            if (label != null) label.setTextColor(on ? scheme.accent : scheme.ink);
            return this;
        }

        IconButton text(String t) {
            if (label != null) label.setText(t);
            return this;
        }

        IconButton kind(int k) {
            icon.setKind(k);
            return this;
        }

        IconButton dim(boolean dim) {
            setAlpha(dim ? .5f : 1f);
            setEnabled(!dim);
            return this;
        }
    }

    /** Primary action: the page's ink, inverted. */
    static TextView solid(Context c, Scheme s, CharSequence label, Runnable action) {
        TextView t = text(c, label, 15f, s.inverseInk);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(.06f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setMinHeight(dp(c, 48));
        t.setBackground(states(
                fill(s.inverseBg, 12, c),
                fill(withAlpha(s.inverseBg, 218), 12, c),
                fill(withAlpha(s.inverseBg, 46), 12, c)));
        t.setTextColor(colors(s.inverseInk, withAlpha(s.inverseInk, 120)));
        t.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        t.setClickable(true);
        t.setOnClickListener(v -> action.run());
        press(t);
        return t;
    }

    static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /* ---------------------------------------------------------------- toggle */

    /** A 46x26dp switch drawn from scratch; the platform one looks like 2014. */
    static final class Toggle extends View {
        private boolean on;
        private float phase;
        private final Scheme scheme;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float w, h;
        private Runnable change;

        Toggle(Context c, Scheme s, boolean on) {
            super(c);
            this.scheme = s;
            this.on = on;
            this.phase = on ? 1f : 0f;
            float d = density(c);
            w = 46 * d;
            h = 26 * d;
            thumb.setColor(s.dark ? 0xFFF3EEE4 : 0xFFFFFFFF);
            setClickable(true);
            setFocusable(true);
            setContentDescription(on ? "已开启" : "已关闭");
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension((int) w, (int) h);
        }

        @Override protected void onDraw(Canvas canvas) {
            float r = h / 2f;
            track.setColor(blend(scheme.line, scheme.accent, phase));
            canvas.drawRoundRect(new RectF(0, 0, w, h), r, r, track);

            float inset = 3 * density(getContext());
            float cy = h / 2f;
            float tr = (h - 2 * inset) / 2f;
            // Centre must include the thumb radius at both ends, not just the gap.
            float endInset = inset + tr;
            float cx = endInset + (w - 2 * endInset) * phase;
            if (phase < .5f) {
                // A hairline ring keeps the off thumb from floating on paper.
                Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
                edge.setStyle(Paint.Style.STROKE);
                edge.setStrokeWidth(Math.max(1, density(getContext()) * .8f));
                edge.setColor(withAlpha(scheme.ink, 40));
                canvas.drawCircle(cx, cy, tr - .5f, edge);
            }
            canvas.drawCircle(cx, cy, tr, thumb);
        }

        void setOnChange(Runnable r) { change = r; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override public boolean performClick() {
            on = !on;
            setContentDescription(on ? "已开启" : "已关闭");
            ValueAnimator a = ValueAnimator.ofFloat(phase, on ? 1f : 0f);
            a.setDuration(170);
            a.setInterpolator(new DecelerateInterpolator());
            a.addUpdateListener(x -> { phase = (float) x.getAnimatedValue(); invalidate(); });
            a.start();
            if (change != null) change.run();
            return true;
        }

        void setOn(boolean v) {
            if (on == v) return;
            on = v;
            phase = v ? 1f : 0f;
            invalidate();
        }

        boolean isOn() { return on; }
    }

    static int blend(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int aa = (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f);
        int rr = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int gg = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bb = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.argb(aa, rr, gg, bb);
    }

    /* ---------------------------------------------------------------- slider */

    /** Continuous slider with a round knob; no platform SeekBar chrome. */
    static final class Slider extends View {
        private final Scheme scheme;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float value;
        private Runnable change;

        Slider(Context c, Scheme s, float value) {
            super(c);
            scheme = s;
            this.value = Math.max(0f, Math.min(1f, value));
            paint.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        void setOnChange(Runnable r) { change = r; }

        float value() { return value; }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), dp(getContext(), 34));
        }

        @Override protected void onDraw(Canvas canvas) {
            float pad = dp(getContext(), 9);
            float cy = getHeight() / 2f;
            float x0 = pad, x1 = getWidth() - pad;
            float knobX = x0 + (x1 - x0) * value;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2.5f));
            paint.setColor(scheme.line);
            canvas.drawLine(x0, cy, x1, cy, paint);
            paint.setColor(scheme.accent);
            canvas.drawLine(x0, cy, Math.max(x0 + 1, knobX), cy, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(scheme.dark ? 0xFF23272E : 0xFFFFFDF8);
            canvas.drawCircle(knobX, cy, dp(getContext(), 6.5f), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 1.8f));
            paint.setColor(scheme.accent);
            canvas.drawCircle(knobX, cy, dp(getContext(), 6.5f), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE
                    || e.getAction() == MotionEvent.ACTION_UP) {
                float pad = dp(getContext(), 9);
                float span = Math.max(1, getWidth() - 2 * pad);
                float v = Math.max(0f, Math.min(1f, (e.getX() - pad) / span));
                if (Math.abs(v - value) > .002f) {
                    value = v;
                    invalidate();
                    if (change != null) change.run();
                }
            }
            return true;
        }
    }

    /* ------------------------------------------------------------------ chip */

    static final class Chip extends TextView {        private boolean on;
        private final Scheme scheme;
        private Runnable change;

        Chip(Context c, Scheme s, CharSequence label, boolean on) {
            super(c);
            this.scheme = s;
            this.on = on;
            setText(label);
            setTextSize(12);
            setLetterSpacing(.1f);
            setAllCaps(true);
            setGravity(Gravity.CENTER);
            setIncludeFontPadding(false);
            setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
            setClickable(true);
            paint();
        }

        private void paint() {
            setBackground(box(on ? scheme.accentSoft : 0x00000000, 20,
                    on ? scheme.accent : scheme.line, .9f, getContext()));
            setTextColor(on ? scheme.accent : scheme.muted);
        }

        void setOnToggle(Runnable r) { change = r; }

        boolean isOn() { return on; }

        void setOn(boolean v) {
            if (on == v) return;
            on = v;
            paint();
        }

        @Override public boolean performClick() {
            on = !on;
            paint();
            if (change != null) change.run();
            return true;
        }
    }

    /* ------------------------------------------------------------------ icons */

    static final int CARD = 1, LIST = 2, SLIDERS = 3, WINDOW = 4, SPEAKER = 5,
            STAR = 6, STAR_ON = 7, PLUS = 8, MINUS = 9, CLOSE = 10, GRIP = 11,
            CHEVRON = 12, BACK = 13, SEARCH = 14;

    /**
     * Line icons drawn on a 24x24 grid. Keeping them in code avoids mipmap sets
     * and keeps stroke weight consistent with the 0.8dp hairlines.
     */
    static final class Ico extends View {
        private int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        Ico(Context c, int kind, int color, float sizeDp) {
            super(c);
            this.kind = kind;
            this.color = color;
            setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        }

        void setKind(int k) {
            kind = k;
            invalidate();
        }

        void tint(int c) {
            color = c;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float d = density(getContext());
            float size = Math.min(getWidth(), getHeight());
            float s = size / 24f;
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(s, s);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.6f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            switch (kind) {
                case CARD:
                    round(canvas, 4, 5.5f, 20, 18.5f, 2.6f, false);
                    line(canvas, 8, 10.5f, 16, 10.5f);
                    line(canvas, 8, 14.5f, 12.5f, 14.5f);
                    break;
                case LIST:
                    line(canvas, 4, 7, 20, 7);
                    line(canvas, 4, 12, 15.5f, 12);
                    line(canvas, 4, 17, 20, 17);
                    line(canvas, 18, 12, 20, 12);
                    break;
                case SLIDERS:
                    line(canvas, 4, 8.5f, 20, 8.5f);
                    line(canvas, 4, 15.5f, 20, 15.5f);
                    p.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(15.5f, 8.5f, 2.6f, p);
                    canvas.drawCircle(8.5f, 15.5f, 2.6f, p);
                    break;
                case WINDOW:
                    p.setAlpha(120);
                    round(canvas, 4, 4, 14, 14, 2.2f, false);
                    p.setAlpha(255);
                    round(canvas, 9, 9, 20, 20, 2.2f, true);
                    break;
                case SPEAKER:
                    p.setStyle(Paint.Style.FILL);
                    Path sp = new Path();
                    sp.moveTo(4.5f, 9.5f);
                    sp.lineTo(8, 9.5f);
                    sp.lineTo(12.5f, 5.5f);
                    sp.lineTo(12.5f, 18.5f);
                    sp.lineTo(8, 14.5f);
                    sp.lineTo(4.5f, 14.5f);
                    sp.close();
                    canvas.drawPath(sp, p);
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawArc(new RectF(12, 8, 18.5f, 16), -55, 110, false, p);
                    p.setAlpha(110);
                    canvas.drawArc(new RectF(12, 4.5f, 21, 19.5f), -55, 110, false, p);
                    break;
                case STAR:
                case STAR_ON:
                    Path star = starPath(12, 12.4f, 8.2f, 3.6f);
                    if (kind == STAR_ON) {
                        p.setStyle(Paint.Style.FILL);
                        canvas.drawPath(star, p);
                    } else {
                        canvas.drawPath(star, p);
                    }
                    break;
                case PLUS:
                    line(canvas, 12, 6, 12, 18);
                    line(canvas, 6, 12, 18, 12);
                    break;
                case MINUS:
                    line(canvas, 6, 12, 18, 12);
                    break;
                case CLOSE:
                    line(canvas, 7, 7, 17, 17);
                    line(canvas, 17, 7, 7, 17);
                    break;
                case GRIP:
                    p.setStyle(Paint.Style.FILL);
                    for (int row = 0; row < 3; row++)
                        for (int col = 0; col < 2; col++)
                            canvas.drawCircle(9.5f + col * 5, 7.5f + row * 4.5f, 1.1f, p);
                    break;
                case CHEVRON:
                    line(canvas, 9, 6, 15, 12);
                    line(canvas, 15, 12, 9, 18);
                    break;
                case BACK:
                    line(canvas, 14, 6, 8, 12);
                    line(canvas, 8, 12, 14, 18);
                    break;
                case SEARCH:
                    p.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(10.5f, 10.5f, 6, p);
                    line(canvas, 15, 15, 20, 20);
                    break;
                default:
                    break;
            }
            canvas.restore();
        }

        private void round(Canvas canvas, float l, float t, float r, float b, float rad, boolean fillIt) {
            RectF f = new RectF(l, t, r, b);
            if (fillIt) {
                int save = p.getAlpha();
                p.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(f, rad, rad, p);
                p.setStyle(Paint.Style.STROKE);
                p.setAlpha(save);
            } else {
                canvas.drawRoundRect(f, rad, rad, p);
            }
        }

        private void line(Canvas canvas, float x1, float y1, float x2, float y2) {
            canvas.drawLine(x1, y1, x2, y2, p);
        }

        private Path starPath(float cx, float cy, float outer, float inner) {
            Path path = new Path();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5;
                float r = (i % 2 == 0) ? outer : inner;
                float x = (float) (cx + Math.cos(a) * r);
                float y = (float) (cy + Math.sin(a) * r);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
            return path;
        }
    }
}
