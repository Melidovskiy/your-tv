package io.freemytv;

import android.app.Application;

/** Gives Prefs a context and Lang its language in every process. */
public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Lang.set(Prefs.language());
    }
}
