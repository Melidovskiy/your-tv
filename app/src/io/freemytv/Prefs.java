package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings changed on the TV itself (Free My TV > Settings). config.properties only gives the first
 * values; anything the user changes here wins. Read from the :home process (home screen, screensaver, settings).
 */
final class Prefs {
    private Prefs() {}

    /** Remote keys we handle, with the app each opens until the user picks another. Codes read from the TCL's log. */
    static final int KEY_NETFLIX = 4062, KEY_YOUTUBE = 190, KEY_PRIME = 4099, KEY_TCL_CHANNEL = 4020;
    static final int[] KEYS = {KEY_NETFLIX, KEY_YOUTUBE, KEY_PRIME, KEY_TCL_CHANNEL};

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;

    private static Context app;

    static void init(Context c) {
        app = c.getApplicationContext();
    }

    private static SharedPreferences sp() {
        return app.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    private static void put(String key, String value) {
        sp().edit().putString(key, value).apply();
        version++;
    }

    private static void put(String key, boolean value) {
        sp().edit().putBoolean(key, value).apply();
        version++;
    }

    // MARK: - Language

    /** "ru" or "en"; until chosen, the TV's own language (Russian for a Russian TV, English otherwise). */
    static String language() {
        String v = sp().getString("language", null);
        if (v == null) v = "ru".equals(Locale.getDefault().getLanguage()) ? "ru" : "en";
        return v.equals("en") ? "en" : "ru";
    }

    static void setLanguage(String v) {
        put("language", v);
        Lang.set(v);
    }

    /** Reads the settings file again: the main process caches it, and the :home process changes it. */
    @SuppressWarnings("deprecation")
    static void reload() {
        app.getSharedPreferences("settings", Context.MODE_MULTI_PROCESS);
        Lang.set(language());
    }

    // MARK: - Units

    /** "auto", "C" or "F". */
    static String temperature() {
        String v = sp().getString("temperature", Config.TEMPERATURE).trim().toUpperCase(Locale.ROOT);
        return v.startsWith("F") ? "F" : v.startsWith("C") ? "C" : "auto";
    }

    static void setTemperature(String v) { put("temperature", v); }

    /** "auto" (dark from sunset to sunrise), "light" or "dark". */
    static String theme() {
        // Dark by default: white text reads best over the forest wallpaper.
        String v = sp().getString("theme", "dark");
        return v.equals("light") || v.equals("dark") ? v : "auto";
    }

    static void setTheme(String v) { put("theme", v); }

    /** "auto" (the TV's own 12/24-hour setting), "24" or "12". */
    static String clock() {
        String v = sp().getString("clock", "auto");
        return v.equals("24") || v.equals("12") ? v : "auto";
    }

    static void setClock(String v) { put("clock", v); }

    /** 12-hour time ("9:41 PM"), as in the US: chosen in Home Settings, or the TV's own setting until then. */
    static boolean twelveHour() {
        String v = clock();
        return v.equals("auto") ? !android.text.format.DateFormat.is24HourFormat(app) : v.equals("12");
    }

    // MARK: - City (from config.properties)

    static String city() { return Config.CITY.trim(); }
    static String latitude() { return Config.LATITUDE; }
    static String longitude() { return Config.LONGITUDE; }

    // MARK: - Widgets

    static boolean weather() { return sp().getBoolean("weather", true); }
    static void setWeather(boolean on) { put("weather", on); }

    /** The «Для вас» row: the tabs and the three recommendation cards. */
    static boolean picks() { return sp().getBoolean("picks", true); }
    static void setPicks(boolean on) { put("picks", on); }

    // MARK: - Shelf

    /** Packages on the home shelf, in order; null until the user edits it (then the built-in list applies). */
    static List<String> shelf() {
        String v = sp().getString("shelf", null);
        if (v == null) return null;
        List<String> out = new ArrayList<>();
        for (String p : v.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setShelf(List<String> packages) {
        StringBuilder sb = new StringBuilder();
        for (String p : packages) sb.append(sb.length() == 0 ? "" : ",").append(p);
        put("shelf", sb.toString());
    }

    /** Apps kept out of the dock altogether (still in «Все приложения»). */
    static java.util.Set<String> dockHidden() {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String p : sp().getString("dockHidden", "").split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setDockHidden(java.util.Set<String> packages) {
        put("dockHidden", String.join(",", packages));
    }

    // MARK: - Remote buttons

    private static String defaultApp(int keyCode) {
        switch (keyCode) {
            case KEY_NETFLIX: return "com.netflix.ninja";
            case KEY_YOUTUBE: return "com.google.android.youtube.tv";
            case KEY_PRIME: return "com.amazon.amazonvideo.livingroom";
            case KEY_TCL_CHANNEL: return "";
            default: return "";
        }
    }

    /** Package the key opens ("" = nothing). */
    static String keyApp(int keyCode) { return sp().getString("key" + keyCode, defaultApp(keyCode)); }
    static void setKeyApp(int keyCode, String pkg) { put("key" + keyCode, pkg); }
}
