package io.freemytv;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The VPN to your own server, as the rest of the app sees it: start, stop, YouTube only or the whole TV, and the
 * current state. The work happens in TunService (main process); the state goes through a small file and a broadcast,
 * so the home screen (the :home process) can show it.
 */
final class Vpn {
    private Vpn() {}

    /** Xray's local SOCKS inbound: this app's requests that must go through the server (TMDB). */
    static final int SOCKS_PORT = 10808;

    enum State { OFF, CONNECTING, YOUTUBE, WHOLE_TV, NO_SERVER }

    static final String ACTION_STATE = "io.freemytv.VPN_STATE";
    static final String START = "io.freemytv.START", WHOLE_TV = "io.freemytv.WHOLE_TV",
            YOUTUBE_ONLY = "io.freemytv.YOUTUBE_ONLY", STOP = "io.freemytv.STOP";
    private static final String STATE_FILE = "vpn_state";

    /** Whether config.properties has a server link. */
    static boolean configured() {
        return !Config.PROXY_LINK.trim().isEmpty();
    }

    /** Starts the VPN in its saved mode (YouTube only unless the whole TV was chosen). */
    static void start(Context c) { send(c, START); }

    static void setWholeTv(Context c, boolean on) { send(c, on ? WHOLE_TV : YOUTUBE_ONLY); }

    static void stop(Context c) { send(c, STOP); }

    private static void send(Context c, String action) {
        if (!configured()) return;
        // A foreground service can start from the background (boot, jobs); TunService calls startForeground at once.
        c.startForegroundService(new Intent(c, TunService.class).setAction(action));
    }

    static State read(Context c) {
        try {
            String s = new String(Files.readAllBytes(new File(c.getFilesDir(), STATE_FILE).toPath()), StandardCharsets.UTF_8).trim();
            return State.valueOf(s);
        } catch (IOException | IllegalArgumentException e) {
            return State.OFF;
        }
    }

    /** Saves the state and tells this app's other process (TunService calls this). */
    static void write(Context c, State state) {
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), STATE_FILE))) {
            out.write(state.name().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            AppLog.w("vpn state: " + e.getMessage());
        }
        c.sendBroadcast(new Intent(ACTION_STATE).setPackage(c.getPackageName()).putExtra("state", state.name()));
    }
}
