package com.kotoba.window;

import android.content.Context;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Last-resort diagnostics. A crash inside startup must leave a readable message,
 * both on screen and in a file that can be pulled off the phone over USB.
 */
final class Crash {

    private Crash() {}

    static void install(final Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            write(app, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static String summary(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getName() + (message == null ? "" : ": " + message);
    }

    static String report(Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter printer = new PrintWriter(buffer);
        error.printStackTrace(printer);
        printer.flush();
        return header() + buffer;
    }

    private static String header() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                + "\n" + Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")\n\n";
    }

    static void write(Context context, Throwable error) {
        save(context, report(error));
    }

    static void save(Context context, String text) {
        File[] targets = {internal(context), external(context)};
        for (File file : targets) {
            if (file == null) continue;
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes("UTF-8"));
            } catch (Throwable ignored) {
                // Diagnostics must never throw.
            }
        }
    }

    static String read(Context context) {
        File file = internal(context);
        if (file == null || !file.exists()) return "";
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buffer.write(chunk, 0, n);
            return buffer.toString("UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    static void clear(Context context) {
        File local = internal(context);
        if (local != null) local.delete();
        File shared = external(context);
        if (shared != null) shared.delete();
    }

    /** App-private copy, always writable. */
    private static File internal(Context c) {
        try {
            return new File(c.getFilesDir(), "last-crash.txt");
        } catch (Throwable e) {
            return null;
        }
    }

    /** Copy reachable from a PC or file manager: Android/data/com.kotoba.window/files/. */
    private static File external(Context c) {
        try {
            File dir = c.getExternalFilesDir(null);
            return dir == null ? null : new File(dir, "last-crash.txt");
        } catch (Throwable e) {
            return null;
        }
    }
}
