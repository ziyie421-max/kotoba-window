package com.kotoba.window;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One modal shape for every prompt: paper panel, serif title, quiet buttons. */
final class Sheet {

    private Sheet() {}

    static void show(Context c, Ui.Scheme s, String title, CharSequence body,
                     boolean scrollable, String ok, Runnable onOk, String cancel, Runnable onCancel) {
        final Dialog[] box = new Dialog[1];
        final Runnable close = () -> {
            if (box[0] != null) box[0].dismiss();
        };

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.box(s.surface, 18, s.line, .8f, c));
        panel.setPadding(Ui.dp(c, 22), Ui.dp(c, 20), Ui.dp(c, 22), Ui.dp(c, 14));

        TextView heading = Ui.text(c, title, 17, s.ink);
        heading.setTypeface(Ui.serif());
        heading.setLetterSpacing(.02f);
        panel.addView(heading);

        TextView text = Ui.paragraph(c, body, 13.5f, s.muted);
        if (scrollable) {
            text.setLineSpacing(Ui.dp(c, 5), 1f);
            text.setMovementMethod(new ScrollingMovementMethod());
            ScrollView scroll = new ScrollView(c);
            scroll.addView(text, new ViewGroup.LayoutParams(-1, -2));
            scroll.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 14));
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, Ui.dp(c, 300)));
        } else {
            text.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 18));
            panel.addView(text);
        }

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (cancel != null) {
            TextView no = Ui.ghost(c, s, cancel, () -> {
                close.run();
                if (onCancel != null) onCancel.run();
            });
            no.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 44));
            lp.rightMargin = Ui.dp(c, 8);
            buttons.addView(no, lp);
        }
        TextView yes = Ui.solid(c, s, ok, () -> {
            close.run();
            if (onOk != null) onOk.run();
        });
        yes.setPadding(Ui.dp(c, 22), 0, Ui.dp(c, 22), 0);
        buttons.addView(yes, new LinearLayout.LayoutParams(-2, Ui.dp(c, 44)));
        panel.addView(buttons);

        Dialog dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(.6f);
            w.setGravity(Gravity.CENTER);
        }
        box[0] = dialog;
        dialog.show();
        if (w != null) {
            int width = (int) (c.getResources().getDisplayMetrics().widthPixels * .88f);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    static void note(Context c, Ui.Scheme s, String title, CharSequence body) {
        show(c, s, title, body, false, "好", null, null, null);
    }
}
