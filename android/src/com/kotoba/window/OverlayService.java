package com.kotoba.window;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The floating card: a draggable paper slip with a slim handle bar. Collapsed it is
 * only the handle, so it can sit at the edge of whatever app you are reading.
 */
public class OverlayService extends Service {

    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private StudyView study;
    private boolean collapsed;
    private int fullHeight;
    private Ui.Ico collapseIcon;
    private Ui.IconButton collapseBtn;
    private BroadcastReceiver screen;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_NOT_STICKY; }

    @Override public void onCreate() {
        super.onCreate();
        notifyForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        try {
            start();
        } catch (Exception e) {
            Toast.makeText(this, "无法显示悬浮窗：" + e.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void notifyForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("study", "悬浮词卡", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "study")
                .setSmallIcon(R.drawable.icon_small)
                .setContentTitle("小词窗 · 悬浮中")
                .setContentText("点按返回背词界面并收起悬浮窗")
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    private void start() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        study = new StudyView(this, true);
        final Ui.Scheme s = study.s;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Ui.box(Ui.withAlpha(s.bg, paperAlpha(study)), 16,
                Ui.withAlpha(s.line, 190), .9f, this));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 6), 0);
        bar.addView(new Ui.Ico(this, Ui.GRIP, s.faint, 11));

        TextView mark = Ui.text(this, "あ", 11, s.accent);
        mark.setTypeface(Ui.serif());
        mark.setPadding(Ui.dp(this, 8), 0, 0, 0);
        bar.addView(mark);

        TextView title = Ui.text(this, "小词窗", 10.5f, s.muted);
        title.setLetterSpacing(.18f);
        title.setPadding(Ui.dp(this, 6), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Ui.IconButton collapse = new Ui.IconButton(this, s, Ui.MINUS, null);
        collapse.setBackground(Ui.states(Ui.fill(0x00000000, 20, this),
                Ui.fill(s.accentSoft, 20, this), Ui.fill(0x00000000, 20, this)));
        collapse.setContentDescription("收起词卡");
        collapseIcon = collapse.icon;
        collapseBtn = collapse;
        bar.addView(collapse, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));

        Ui.IconButton close = new Ui.IconButton(this, s, Ui.CLOSE, null);
        close.setBackground(Ui.states(Ui.fill(0x00000000, 20, this),
                Ui.fill(s.accentSoft, 20, this), Ui.fill(0x00000000, 20, this)));
        close.setContentDescription("关闭悬浮窗");
        bar.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30)));
        close.onClick(() -> stopSelf());

        root.addView(bar, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
        root.addView(Ui.hair(this, s.hair));
        root.addView(study, new LinearLayout.LayoutParams(-1, 0, 1));

        int width = Math.min(Ui.dp(this, 330), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24));
        fullHeight = Math.min(Ui.dp(this, 566), getResources().getDisplayMetrics().heightPixels - Ui.dp(this, 120));

        params = new WindowManager.LayoutParams(width, fullHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = Ui.dp(this, 12);
        params.y = Ui.dp(this, 84);

        collapse.onClick(() -> setCollapsed(!collapsed));

        bar.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;

            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = params.x;
                    startY = params.y;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    v.animate().alpha(.82f).setDuration(90).start();
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = Math.max(0, Math.min(getResources().getDisplayMetrics().widthPixels - params.width,
                            startX + (int) (e.getRawX() - downX)));
                    params.y = Math.max(0, Math.min(getResources().getDisplayMetrics().heightPixels - params.height,
                            startY + (int) (e.getRawY() - downY)));
                    wm.updateViewLayout(root, params);
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().alpha(1f).setDuration(140).start();
                }
                return true;
            }
        });

        wm.addView(root, params);
        study.next();

        screen = new BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                Voice.get(c).stop();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
    }

    private int paperAlpha(StudyView card) {
        if (!card.quiet()) return 255;
        double opacity = card.store.settings().optDouble("BackgroundOpacity", .8);
        return (int) (Math.max(.2, Math.min(1, opacity)) * 255);
    }

    private void setCollapsed(boolean value) {
        collapsed = value;
        Voice.get(this).stop();
        study.setActive(!collapsed);
        study.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        collapseIcon.setKind(collapsed ? Ui.PLUS : Ui.MINUS);
        if (collapseBtn != null) collapseBtn.setContentDescription(collapsed ? "展开词卡" : "收起词卡");
        params.height = collapsed ? Ui.dp(this, 40) : fullHeight;
        wm.updateViewLayout(root, params);
    }

    @Override public void onDestroy() {
        Voice.get(this).stop();
        if (root != null && root.isAttachedToWindow()) wm.removeView(root);
        if (screen != null) unregisterReceiver(screen);
        stopForeground(true);
        super.onDestroy();
    }
}
