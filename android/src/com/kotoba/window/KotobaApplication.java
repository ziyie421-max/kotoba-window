package com.kotoba.window;
/** Capture Java failures before Activity creation, including OEM startup callbacks. */
public final class KotobaApplication extends android.app.Application {
 @Override public void onCreate() { super.onCreate(); Crash.install(this); }
}
