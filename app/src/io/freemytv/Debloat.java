package io.freemytv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Which apps the new mode turns off (pm disable-user: still installed, back with *Заводской*) and which it never
 * touches. Built for the Xiaomi Mi TV 4S (MiTV-MSSP1, Android 9) from its package list. Pure Java (test.sh).
 */
final class Debloat {
    private Debloat() {}

    /** Turned off in the new mode, when the TV has them. The TV's own home screen and screensaver are added at switch time. */
    static final String[] DEBLOAT = {
        "com.mitv.tvhome.atv",                      // PatchWall: Xiaomi's home with ads, autoplay and OneTrack ad tracking
        "com.mitv.tvhome.michannel",                // PatchWall's promo channels on the home screen
        "com.miui.tv.analytics",                    // Xiaomi analytics
        "com.xiaomi.statistic",                     // Xiaomi usage statistics
        "com.google.android.tv.bugreportsender",    // Google's bug report uploader
        "com.google.android.feedback",              // Google's feedback and crash report sender
        "com.google.android.videos",                // Google Play Movies (closed down, still running)
        "com.google.android.play.games",            // Google Play Games
        "com.google.android.youtube.tvmusic",       // YouTube Music
        "com.google.android.leanbacklauncher",      // Google's older spare home screen
        "com.google.android.leanbacklauncher.recommendations",  // and its recommendation rows
        "com.google.android.backdrop",              // Google's ambient screensaver (ours replaces it)
        "com.mitv.dream",                           // Xiaomi's screensaver, with its ads
        "com.android.printspooler",                 // printing: nothing to print from a TV
        "org.chromium.webview_shell",               // WebView's test browser
    };

    /** The voice assistant, only with disable_assistant=true in config.properties (the mic button stops working). */
    static final String[] ASSISTANT = {"com.google.android.katniss", "com.android.hotwordenrollment.okgoogle",
            "com.google.android.speech.pumpkin"};   // and its offline speech engine

    /** Never turned off, not even by mistake (picture, sound, inputs, remote, updates, store). Checked before every disable. */
    static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
        // Google and Android
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.apps.mediashell",
        "com.google.android.tv.remote.service", "com.google.android.inputmethod.latin", "com.google.android.webview",
        "com.android.tv.settings", "com.android.systemui", "com.android.providers.tv", "android", "io.freemytv",
        // Xiaomi: TV services, resources, the TV manager, system updates, setup, legal, USB media, overlays, web pages
        "mitv.service", "com.xiaomi.mitv.res", "com.xiaomi.mitv.tvmanager", "com.xiaomi.mitv.updateservice",
        "com.xiaomi.android.tvsetup.partnercustomizer", "com.xiaomo.tv.milegal", "com.xiaomi.mitv.mediaexplorer",
        "com.mitv.videoplayer", "com.mitv.gallery", "com.xiaomi.floatingframe", "com.xm.webcontent",
        // MediaTek: remote keys, tuner and live TV, inputs, network, setup, factory menu
        "com.mediatek.hotkey.dispatcher", "com.mediatek.tv.agent", "com.mediatek.tvinput",
        "com.mediatek.tvinputservice.arbitratorservice", "com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.setupwizard",
        "com.mediatek.network", "com.mediatek.tv.factory", "mediatek.factorymenu.ui", "com.mediatek.tv.service"));
}
