package io.freemytv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

/**
 * The VPN (main process): Android's VpnService hands a TUN to the Xray core (libv2ray), which sends it to your own
 * proxy server (ProxyLink). YouTube mode: only the YouTube apps (VpnRoutes) go through it; whole-TV mode: every app but this one.
 * The core also runs a local SOCKS port (Vpn.SOCKS_PORT) for this app's own requests. Every minute a test request
 * through the server tells whether it answers. Never logs the link or the config.
 */
public class TunService extends VpnService {
    private static final String CHANNEL = "vpn";
    private static final String PROBE = "https://www.gstatic.com/generate_204";
    private static final long CHECK_EVERY_S = 60;

    private final ScheduledExecutorService work = Executors.newSingleThreadScheduledExecutor();
    private CoreController core;
    private ParcelFileDescriptor tun;
    private ScheduledFuture<?> checks;
    private boolean whole;
    /** The apps the running TUN carries (YouTube mode), to notice a YouTube app installed or removed since. */
    private List<String> carried;
    private int failures;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification());
        String action = intent == null || intent.getAction() == null ? Vpn.START : intent.getAction();
        work.execute(() -> handle(action));
        return START_STICKY;
    }

    private void handle(String action) {
        SharedPreferences prefs = getSharedPreferences("vpn", MODE_PRIVATE);
        switch (action) {
            case Vpn.STOP:
                prefs.edit().putBoolean("off", true).apply();   // stays off, also after a reboot (always-on)
                stopCore();
                Vpn.write(this, Vpn.State.OFF);
                stopForeground(true);
                stopSelf();
                return;
            case Vpn.WHOLE_TV:
                prefs.edit().putBoolean("wholeTv", true).putBoolean("off", false).apply();
                break;
            case Vpn.YOUTUBE_ONLY:
                prefs.edit().putBoolean("wholeTv", false).putBoolean("off", false).apply();
                break;
            default:   // START, and the system's own start of an always-on VPN
                if (prefs.getBoolean("off", false)) {   // turned off with the VPN button
                    stopCore();
                    Vpn.write(this, Vpn.State.OFF);
                    stopForeground(true);
                    stopSelf();
                    return;
                }
                // Already running in the saved mode: nothing to do (the home screen asks on every resume).
                if (core != null && core.getIsRunning() && whole == prefs.getBoolean("wholeTv", false)
                        && (whole || youtubeApps().equals(carried))) return;
                break;
        }
        whole = prefs.getBoolean("wholeTv", false);
        startCore();
    }

    private void startCore() {
        stopCore();
        if (VpnService.prepare(this) != null) {
            AppLog.w("vpn: not allowed yet (the new mode's switch allows it)");
            Vpn.write(this, Vpn.State.OFF);
            return;
        }
        Vpn.write(this, Vpn.State.CONNECTING);
        try {
            String link = Config.PROXY_LINK.trim();
            ProxyLink.outbound(link);   // a bad link fails here, before the TUN is set up
            tun = establish();
            if (core == null) {
                Libv2ray.initCoreEnv(getFilesDir().getAbsolutePath(), "");
                core = Libv2ray.newCoreController(new Callbacks());
            }
            core.startLoop(XrayConfig.build(link, Vpn.SOCKS_PORT, tun != null), tun == null ? 0 : tun.getFd());
            failures = 0;
            checks = work.scheduleWithFixedDelay(this::check, 3, CHECK_EVERY_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            AppLog.w("vpn: could not start: " + e.getClass().getSimpleName());
            stopCore();
            Vpn.write(this, Vpn.State.NO_SERVER);
        }
    }

    /**
     * The TUN for the current mode, or null when no YouTube app is installed in YouTube mode: an empty allowed list
     * would send every app through the VPN.
     */
    private ParcelFileDescriptor establish() throws PackageManager.NameNotFoundException {
        Builder b = new Builder().setSession(getString(R.string.app_name)).setMtu(1500)
                .addAddress("10.10.14.1", 30).addRoute("0.0.0.0", 0).addDnsServer("1.1.1.1");
        if (whole) {
            b.addDisallowedApplication(getPackageName());
        } else {
            List<String> allowed = youtubeApps();
            carried = allowed;
            if (allowed.isEmpty()) return null;
            for (String pkg : allowed) b.addAllowedApplication(pkg);
        }
        return b.establish();
    }

    private List<String> youtubeApps() {
        Set<String> installed = new HashSet<>();
        for (ApplicationInfo a : getPackageManager().getInstalledApplications(0)) installed.add(a.packageName);
        return VpnRoutes.allowed(installed);
    }

    /** A request through the server: two failures in a row mean it doesn't answer. */
    private void check() {
        CoreController c = core;
        if (c == null) return;
        boolean ok;
        try {
            ok = c.measureDelay(PROBE) > 0;
        } catch (Exception e) {
            ok = false;
        }
        failures = ok ? 0 : failures + 1;
        if (ok) Vpn.write(this, whole ? Vpn.State.WHOLE_TV : Vpn.State.YOUTUBE);
        else if (failures >= 2) Vpn.write(this, Vpn.State.NO_SERVER);
    }

    private void stopCore() {
        if (checks != null) {
            checks.cancel(false);
            checks = null;
        }
        if (core != null) {
            try {
                core.stopLoop();
            } catch (Exception e) {
                AppLog.w("vpn: stop: " + e.getClass().getSimpleName());
            }
        }
        if (tun != null) {
            try {
                tun.close();
            } catch (Exception ignored) {
            }
            tun = null;
        }
    }

    /** Another VPN took over, or the permission was taken back. */
    @Override public void onRevoke() {
        work.execute(() -> {
            stopCore();
            Vpn.write(this, Vpn.State.OFF);
            stopSelf();
        });
    }

    @Override public void onDestroy() {
        work.execute(this::stopCore);
        work.shutdown();
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_MIN));
        }
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.icon).setContentTitle(Lang.tr("VPN работает", "VPN is on"))
                .setOngoing(true).build();
    }

    private static final class Callbacks implements CoreCallbackHandler {
        @Override public long onEmitStatus(long code, String message) { return 0; }
        @Override public long shutdown() { return 0; }
        @Override public long startup() { return 0; }
    }
}
