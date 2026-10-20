package io.freemytv;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Original ⇄ Free My TV. "free": our home screen and screensaver, and the apps in the lists below turned off
 * (pm disable-user: still installed, back with one button). "original": everything as it came from the factory.
 *
 * It all goes through the TV's own ADB (Adb, 127.0.0.1:5555), which you allow once on the TV. The switches run as
 * background jobs (ModeJob) in the main process; the settings screen (the :home process) asks for them and follows the
 * state file. Only the TV itself asks for switches: nothing here listens to the network.
 */
final class HomeMode {
    private HomeMode() {}

    static final String PKG = "io.freemytv";
    static final String HOME_ACTIVITY = PKG + "/" + PKG + ".HomeActivity";
    static final String SETTINGS_ACTIVITY = PKG + "/" + PKG + ".SettingsActivity";
    /** Our screensaver: on Android 11 the copy that runs in the main process (ScreensaverAlone, #10). */
    static final String DREAM = PKG + "/" + PKG + (Build.VERSION.SDK_INT == 30 ? ".ScreensaverAlone" : ".Screensaver");
    static final String FREE = "free", ORIGINAL = "original";

    /** Classic Android TV's home screen; its recommendations service crashes on the screensaver while it's off (#11). */
    static final String TV_LAUNCHER = "com.google.android.tvlauncher";
    static final String TV_RECOMMENDATIONS = "com.google.android.tvrecommendations";
    /** Google TV's home screen: the fallback when the TV's own one wasn't recorded. */
    static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity";
    /**
     * Google TV has a RecoveryActivity that would take over the home screen without its launcher. Older Android TV
     * (Android 9) has the same setup app without a home screen: there it is left alone.
     */
    static final String GOOGLE_SETUP = "com.google.android.tungsten.setupwraith";

    /**
     * Android settings the new mode changes (saved first, restored by Original): Private DNS and animation speed
     * (global), and this app as the always-on VPN (secure).
     */
    private static final String[] TWEAKED = {"global private_dns_mode", "global private_dns_specifier",
            "global window_animation_scale", "global transition_animation_scale", "global animator_duration_scale",
            "secure always_on_vpn_app", "secure always_on_vpn_lockdown"};
    /** AdGuard's public DNS: blocks ad and tracking domains for every app on the TV. */
    static final String AD_DNS = "dns.adguard-dns.com";

    /** One switch at a time (jobs can overlap: a boot check and a button press). */
    private static final Object RUN = new Object();
    private static final Object FILE_LOCK = new Object();

    // MARK: - Requests (from any process)

    /** Asks the main process to switch (the settings screen calls this). */
    static void request(Context c, String mode) {
        ModeJob.schedule(c, ModeJob.APPLY, mode, 0);
    }

    /** Our home screen crashed 3 times in 5 minutes: give the home screen back to the TV's own (asked by :home). */
    static void requestRevertHome(Context c) {
        ModeJob.schedule(c, ModeJob.REVERT, null, 0);
    }

    /** After the TV boots (and after a TV update): wait for the system to settle and check Free My TV mode. */
    static void bootCheck(Context c) {
        ensureRemoteService(c, 30_000);
        ModeJob.schedule(c, ModeJob.VERIFY, null, 45_000);
        ModeJob.schedulePeriodic(c, ModeJob.DNS_CHECK, 30 * 60_000L);
        if (FREE.equals(state(c).optString("mode"))) Vpn.start(c);
    }

    /** After an update of this app: complete Free My TV mode if anything is missing. */
    static void verify(Context c) {
        ModeJob.schedule(c, ModeJob.VERIFY, null, 0);
        if (FREE.equals(state(c).optString("mode"))) Vpn.start(c);   // an update stops the running VPN
    }

    /**
     * The remote's app buttons (RemoteKeys) need an accessibility service that only filters those keys. It is on only
     * on TCL TVs in Free My TV mode, and goes off with Original. Android turns it off when this app is updated, so the
     * boot check and an update turn it back on.
     */
    static void ensureRemoteService(Context c, long delayMs) {
        if (RemoteKeys.supported()) ModeJob.schedule(c, ModeJob.REMOTE, null, delayMs);
    }

    static void ensureRemoteServiceNow(Context app) {
        if (!RemoteKeys.supported() || !FREE.equals(state(app).optString("mode"))) return;
        try (Adb adb = Adb.connect(app, 10_000, null)) {
            remoteService(app, adb, true);
        } catch (IOException e) {
            AppLog.w("remote buttons service: " + e.getMessage());
        }
    }

    private static final String REMOTE_SERVICE = PKG + "/" + PKG + ".RemoteKeyService";
    private static final Pattern SERVICE = Pattern.compile("[\\w.]+/[\\w.$]+");

    /** Adds ours to the enabled accessibility services (keeping the others, like TalkBack), or takes it out. */
    private static void remoteService(Context c, Adb adb, boolean on) throws IOException {
        String current = adb.shell("settings get secure enabled_accessibility_services").trim();
        if (current.equals("null")) current = "";
        List<String> others = new ArrayList<>();
        for (String one : current.split(":")) {
            if (one.isEmpty() || one.equals(REMOTE_SERVICE)) continue;
            // Only plain component names go back into the shell command; anything else is left alone.
            if (!SERVICE.matcher(one).matches()) {
                AppLog.w("accessibility services left as they are: unexpected entry");
                return;
            }
            others.add(one);
        }
        if (on == current.contains(REMOTE_SERVICE)) return;
        List<String> list = new ArrayList<>(others);
        if (on) {
            JSONObject s = state(c);
            try { s.put("accessibilityWasOff", !"1".equals(adb.shell("settings get secure accessibility_enabled").trim())); }
            catch (JSONException ignored) {}
            save(c, s);
            list.add(REMOTE_SERVICE);
        }
        adb.shell(list.isEmpty() ? "settings delete secure enabled_accessibility_services"
                : "settings put secure enabled_accessibility_services '" + String.join(":", list) + "'");
        if (on) adb.shell("settings put secure accessibility_enabled 1");
        else if (list.isEmpty() && state(c).optBoolean("accessibilityWasOff")) adb.shell("settings put secure accessibility_enabled 0");
        AppLog.w("remote buttons service " + (on ? "on" : "off"));
    }

    // MARK: - Switches (main process, from ModeJob)

    static void applyNow(Context c, String mode) {
        synchronized (RUN) {
            if (FREE.equals(mode)) applyFree(c); else applyOriginal(c);
        }
    }

    private static void applyFree(Context c) {
        JSONObject s = state(c);
        try (Adb adb = connect(c, s)) {
            // The TV's own home screen and screensaver, recorded once (before ours replace them) so "original" restores
            // exactly what was there, on any brand.
            String home = originalHome(s);
            if (home.isEmpty() || home.startsWith(PKG + "/")) {
                String found = currentHome(adb);
                home = found != null ? realHome(found) : "";
            }
            String dream = s.optString("originalDream", "");
            if (dream.isEmpty()) dream = defaultDream(adb);
            JSONObject latest = state(c);
            latest.put("originalHome", home).put("originalDream", dream);
            save(c, latest);
            // Turning off an app makes Android delete the Wi-Fi networks it saved (#14), and Original can't bring them
            // back. The TV's first setup usually saved the network: then stop here, before anything changes.
            Set<String> owners = wifiOwners(adb);
            boolean setupHome = setupIsHome(adb);
            if ((setupHome && owners.contains(GOOGLE_SETUP)) || owners.contains(packageOf(home))) {
                throw new IOException("первичная настройка ТВ сохранила сеть Wi-Fi, и её отключение удалит эти сети. "
                        + "В Настройках → Сеть и Интернет забудьте сеть и подключитесь заново (сети вне зоны действия "
                        + "там не видны: их можно удалить с компьютера), затем попробуйте ещё раз.");
            }
            if (metrics(latest, ORIGINAL) == null && !home.isEmpty()) {
                busy(c, "Измеряю заводской главный экран…");
                measureHomeTime(c, adb, ORIGINAL);
                measureMemory(c, adb, ORIGINAL);
            }
            busy(c, "Меняю главный экран и заставку…");
            check(adb.shell("cmd package set-home-activity " + HOME_ACTIVITY), "Success",
                    "Android не принял новый главный экран");
            adb.shell("settings put secure screensaver_components " + DREAM);
            applyTweaks(c, adb);
            List<String> targets = targets(home, dream, setupHome);
            Set<String> disabled = new HashSet<>(stringList(state(c), "disabled"));
            Set<String> present = packages(adb, "");
            int done = 0;
            for (String pkg : targets) {
                done++;
                busy(c, "Отключаю приложения (" + done + "/" + targets.size() + ")…");
                if (Debloat.PROTECTED.contains(pkg) || !present.contains(pkg) || owners.contains(pkg)) continue;
                String out = adb.shell("pm disable-user --user 0 " + pkg);
                if (out.contains("disabled-user")) disabled.add(pkg);
                else AppLog.w("could not turn off " + pkg + ": " + out.trim());
            }
            Watchdog.clear(c);
            s = state(c);
            s.put("mode", FREE).put("since", System.currentTimeMillis()).put("homeReverted", false)
                    .put("disabled", new JSONArray(sorted(disabled))).put("setupHome", setupHome);
            save(c, s);
            if (RemoteKeys.supported()) {
                try {
                    remoteService(c, adb, true);
                } catch (IOException e) {
                    AppLog.w("remote buttons service: " + e.getMessage());
                }
            }
            Vpn.start(c);
            notice(c, "Новый экран включён: новый главный экран и заставка, отключено " + disabled.size() + " "
                    + Ru.plural(disabled.size(), "приложение", "приложения", "приложений") + ".");
            busy(c, "Измеряю новый экран…");
            measureHomeTime(c, adb, FREE);
            done(c, null);
            adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            ModeJob.schedule(c, ModeJob.MEMORY, FREE, 120_000);
        } catch (Exception e) {
            done(c, "Не удалось включить новый экран: " + e.getMessage());
        }
    }

    private static void applyOriginal(Context c) {
        JSONObject s = state(c);
        try (Adb adb = connect(c, s)) {
            String home = originalHome(s);
            if (!originalHomeInstalled(adb, home)) {
                // The factory home screens were uninstalled: there is nothing to go back to, so stay.
                done(c, "Заводской главный экран удалён с ТВ: остаётся новый.");
                return;
            }
            busy(c, "Включаю приложения обратно…");
            Set<String> toEnable = new HashSet<>(stringList(s, "disabled"));
            for (String pkg : targets(home, s.optString("originalDream", ""), true)) {
                if (!Arrays.asList(Debloat.DEBLOAT).contains(pkg) && !Arrays.asList(Debloat.ASSISTANT).contains(pkg)) toEnable.add(pkg);
            }
            Set<String> present = packages(adb, "");
            int done = 0;
            List<String> failed = new ArrayList<>();
            for (String pkg : sorted(toEnable)) {
                done++;
                busy(c, "Включаю приложения (" + done + "/" + toEnable.size() + ")…");
                if (!present.contains(pkg)) continue;
                // Checked: whatever didn't come back stays on the list (and in the notice); nothing disappears silently.
                String out = adb.shell("pm enable " + pkg);
                if (!out.contains("new state: enabled")) failed.add(pkg);
            }
            Vpn.stop(c);
            busy(c, "Возвращаю заводской главный экран…");
            boolean homeOk = restoreHome(adb, home);
            adb.shell("settings delete secure screensaver_components");
            restoreTweaks(c, adb);
            try {
                remoteService(c, adb, false);
            } catch (IOException e) {
                AppLog.w("remote buttons service: " + e.getMessage());
            }
            s = state(c);
            s.put("mode", ORIGINAL).put("since", System.currentTimeMillis()).put("homeReverted", false)
                    .put("disabled", new JSONArray(failed));
            save(c, s);
            if (failed.isEmpty() && homeOk) {
                notice(c, "Заводской режим: главный экран, заставка и приложения как с завода.");
            } else {
                List<String> problems = new ArrayList<>(failed);
                if (!homeOk) problems.add("заводской главный экран");
                notice(c, "Заводской режим, но не удалось включить: "
                        + String.join(", ", problems) + ".");
            }
            busy(c, "Измеряю заводской главный экран…");
            measureHomeTime(c, adb, ORIGINAL);
            done(c, null);
            adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            ModeJob.schedule(c, ModeJob.MEMORY, ORIGINAL, 120_000);
        } catch (Exception e) {
            done(c, "Не удалось вернуть заводской режим: " + e.getMessage());
        }
    }

    /**
     * Saves the TWEAKED settings (only the first time: the values before this app changed anything), then sets the
     * animations to half length and, unless turned off in the settings, the ad-blocking DNS.
     */
    private static void applyTweaks(Context c, Adb adb) throws IOException, JSONException {
        JSONObject s = state(c);
        if (s.optJSONObject("saved") == null) {
            JSONObject saved = new JSONObject();
            for (String key : TWEAKED) saved.put(key, adb.shell("settings get " + key).trim());
            s.put("saved", saved);
            save(c, s);
        }
        for (String key : new String[] {"window_animation_scale", "transition_animation_scale", "animator_duration_scale"}) {
            adb.shell("settings put global " + key + " 0.5");
        }
        setDns(adb, adBlock(c));
        // The VPN: allowed without the "Allow VPN connection?" prompt, and always on, so it comes back after a reboot.
        // Lockdown stays off: if the server is down, apps aren't cut off.
        adb.shell("appops set " + PKG + " ACTIVATE_VPN allow");
        adb.shell("settings put secure always_on_vpn_app " + PKG);
        adb.shell("settings put secure always_on_vpn_lockdown 0");
    }

    /** Puts back what applyTweaks saved ("null" = the setting wasn't there: delete it). */
    private static void restoreTweaks(Context c, Adb adb) throws IOException {
        JSONObject s = state(c);
        JSONObject saved = s.optJSONObject("saved");
        if (saved == null) return;
        for (String key : TWEAKED) {
            String v = saved.optString(key, "null");
            // Only plain values go back into the shell command.
            if (v.equals("null") || !v.matches("[\\w.\\-]+")) adb.shell("settings delete " + key);
            else adb.shell("settings put " + key + " " + v);
        }
        s.remove("saved");
        s.remove("dnsFailingSince");
        save(c, s);
        adb.shell("appops set " + PKG + " ACTIVATE_VPN default");
    }

    private static void setDns(Adb adb, boolean on) throws IOException {
        if (on) {
            adb.shell("settings put global private_dns_specifier " + AD_DNS);
            adb.shell("settings put global private_dns_mode hostname");
        } else {
            adb.shell("settings put global private_dns_mode off");
        }
    }

    // MARK: - Ad-blocking DNS

    /** The ad-blocking DNS switch (Настройки экрана → Блокировка рекламы). On by default; kept in the state file. */
    static boolean adBlock(Context c) {
        return state(c).optBoolean("adBlock", true);
    }

    /** Asks the main process to turn the ad-blocking DNS on or off (the settings screen calls this). */
    static void requestAdBlock(Context c, boolean on) {
        ModeJob.schedule(c, ModeJob.DNS, on ? "on" : "off", 0);
    }

    /** Allows this app's VPN (no prompt) and starts it in the given mode (the VPN button, before the switch). */
    static void requestVpn(Context c, boolean wholeTv) {
        ModeJob.schedule(c, ModeJob.VPN, wholeTv ? "whole" : "youtube", 0);
    }

    static void allowVpnNow(Context app, boolean wholeTv) {
        synchronized (RUN) {
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                adb.shell("appops set " + PKG + " ACTIVATE_VPN allow");
            } catch (Exception e) {
                notice(app, "Не удалось включить VPN: " + e.getMessage());
                return;
            }
        }
        Vpn.setWholeTv(app, wholeTv);
    }

    static void setAdBlockNow(Context app, boolean on) {
        synchronized (RUN) {
            try {
                JSONObject s = state(app);
                s.put("adBlock", on);
                s.remove("dnsFailingSince");
                save(app, s);
                if (!FREE.equals(s.optString("mode"))) return;
                try (Adb adb = Adb.connect(app, 10_000, null)) {
                    setDns(adb, on);
                }
            } catch (Exception e) {
                notice(app, "Не удалось переключить блокировку рекламы: " + e.getMessage());
            }
        }
    }

    /**
     * The guard (DnsGuard): if names stop resolving while the internet itself works, the DNS server is down. After
     * DnsGuard.GRACE_MS of that, turn the blocking off so the TV keeps working, and say so.
     */
    static void checkDnsNow(Context app) {
        synchronized (RUN) {
            JSONObject s = state(app);
            if (!FREE.equals(s.optString("mode")) || !s.optBoolean("adBlock", true)) return;
            if (!internetUp()) return;   // no internet at all: not the DNS server's fault
            String mode = android.provider.Settings.Global.getString(app.getContentResolver(), "private_dns_mode");
            boolean resolves = resolves();
            long now = System.currentTimeMillis(), since = s.optLong("dnsFailingSince", 0);
            try {
                if (resolves || !"hostname".equals(mode)) {
                    if (since != 0) { s.remove("dnsFailingSince"); save(app, s); }
                    return;
                }
                if (since == 0) {
                    s.put("dnsFailingSince", now);
                    save(app, s);
                    ModeJob.schedule(app, ModeJob.DNS_CHECK, null, DnsGuard.GRACE_MS + 5_000);
                    return;
                }
                if (!DnsGuard.shouldTurnOff(mode, false, now - since)) {
                    ModeJob.schedule(app, ModeJob.DNS_CHECK, null, DnsGuard.GRACE_MS + 5_000 - (now - since));
                    return;
                }
                try (Adb adb = Adb.connect(app, 10_000, null)) {
                    setDns(adb, false);
                }
                s.put("adBlock", false);
                s.remove("dnsFailingSince");
                save(app, s);
                notice(app, "Блокировка рекламы выключена: DNS не отвечает. Включить снова: Настройки экрана.");
                AppLog.w("ad-blocking DNS turned off: no answers for " + (now - since) / 1000 + " s");
            } catch (Exception e) {
                AppLog.w("DNS check: " + e.getMessage());
            }
        }
    }

    /** A plain TCP connection to Cloudflare's IP (no DNS involved). */
    private static boolean internetUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("1.1.1.1", 443), 5_000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether the system resolver (through Private DNS) answers. */
    private static boolean resolves() {
        try {
            java.net.InetAddress.getByName("connectivitycheck.gstatic.com");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Whether the recorded home screen (or Google TV's) is still on the TV, for this user. */
    private static boolean originalHomeInstalled(Adb adb, String home) throws IOException {
        Set<String> installed = new HashSet<>();
        for (String line : adb.shell("pm list packages").split("\n")) installed.add(line.trim());
        String pkg = packageOf(home);
        return (pkg != null && installed.contains("package:" + pkg))
                || installed.contains("package:" + packageOf(GOOGLE_HOME));
    }

    /** The recorded home screen; without one, Google TV's; without that, the system picks (we leave the role). */
    private static boolean restoreHome(Adb adb, String home) throws IOException {
        if (home.isEmpty() && adb.shell("pm path " + packageOf(GOOGLE_HOME)).contains("package:")) home = GOOGLE_HOME;
        if (!home.isEmpty()) {
            adb.shell("pm enable " + packageOf(home));
            if (adb.shell("cmd package set-home-activity " + home).contains("Success")) return true;
        }
        adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
        return !homeHolder(adb).contains(PKG);
    }

    /** Safety net: our home screen crashed 3 times in a row. Only the home screen goes back; the rest stays. */
    static void revertHomeNow(Context app) {
        synchronized (RUN) {
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                JSONObject s = state(app);
                String home = originalHome(s);
                if (!originalHomeInstalled(adb, home)) {
                    // No factory home screen left: a bare system screen would be worse than retrying ours.
                    notice(app, "Главный экран упал 3 раза подряд, но заводского больше нет: оставил новый.");
                    return;
                }
                adb.shell("pm enable " + GOOGLE_SETUP);
                restoreHome(adb, home);
                String homePkg = packageOf(home);
                JSONArray kept = new JSONArray();
                for (String pkg : stringList(s, "disabled")) {
                    if (!pkg.equals(GOOGLE_SETUP) && !pkg.equals(homePkg)) kept.put(pkg);
                }
                s.put("homeReverted", true).put("disabled", kept);
                save(app, s);
                notice(app, "Новый главный экран упал 3 раза подряд; вернул заводской.");
                adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            } catch (Exception e) {
                AppLog.w("could not give the home screen back: " + e.getMessage());
            }
        }
    }

    /**
     * In Free My TV mode, checks that everything is still as we left it (the listed apps off, our home screen and
     * screensaver) and completes whatever is missing, with a notice. Runs after the TV boots (TV updates can turn
     * things back on) and after an update of this app.
     */
    static void verifyNow(Context app) {
        synchronized (RUN) {
            JSONObject s = state(app);
            String fingerprint = Build.FINGERPRINT;
            boolean updated = !fingerprint.equals(s.optString("fingerprint", fingerprint));
            try {
                s.put("fingerprint", fingerprint);
                save(app, s);
            } catch (JSONException ignored) {}
            if (!FREE.equals(s.optString("mode"))) return;
            List<String> fixed = new ArrayList<>();
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                Set<String> present = packages(adb, "");
                Set<String> disabledNow = packages(adb, "-d");
                Set<String> owners = wifiOwners(adb);
                boolean reverted = s.optBoolean("homeReverted");
                String home = originalHome(s);
                Set<String> wanted = new HashSet<>(stringList(s, "disabled"));
                for (String pkg : targets(home, s.optString("originalDream", ""), s.optBoolean("setupHome", true))) {
                    if (!reverted || !(pkg.equals(packageOf(home)) || pkg.equals(GOOGLE_SETUP))) wanted.add(pkg);
                }
                Set<String> disabled = new HashSet<>(stringList(s, "disabled"));
                for (String pkg : sorted(wanted)) {
                    if (Debloat.PROTECTED.contains(pkg) || !present.contains(pkg) || owners.contains(pkg)) continue;
                    if (disabledNow.contains(pkg)) {
                        disabled.add(pkg);
                        continue;
                    }
                    if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) {
                        disabled.add(pkg);
                        fixed.add(pkg);
                    }
                }
                if (!reverted && !homeHolder(adb).contains(PKG)) {
                    adb.shell("cmd package set-home-activity " + HOME_ACTIVITY);
                    fixed.add("главный экран");
                }
                if (!adb.shell("settings get secure screensaver_components").contains(DREAM)) {
                    adb.shell("settings put secure screensaver_components " + DREAM);
                    fixed.add("заставку");
                }
                JSONObject latest = state(app);
                latest.put("disabled", new JSONArray(sorted(disabled)));
                save(app, latest);
            } catch (Exception e) {
                notice(app, "Не удалось проверить новый экран: " + e.getMessage());
                return;
            }
            if (!fixed.isEmpty()) {
                notice(app, (updated ? "Обновление ТВ снова включило "
                        : "Отключил недостающее: ") + String.join(", ", fixed) + ".");
            } else if (updated) {
                notice(app, "ТВ обновился; новый экран по-прежнему включён.");
            }
        }
    }

    /**
     * What to turn off: the TV's own home screen and screensaver, Google TV's recovery home (when setupHome: the setup
     * app is one of the TV's home screens), and the lists above.
     */
    private static List<String> targets(String home, String dream, boolean setupHome) {
        List<String> out = new ArrayList<>();
        String homePkg = packageOf(home);
        if (homePkg != null && !homePkg.equals(PKG)) out.add(homePkg);
        if (TV_LAUNCHER.equals(homePkg)) out.add(TV_RECOMMENDATIONS);
        if (setupHome) out.add(GOOGLE_SETUP);
        String dreamPkg = packageOf(dream);
        if (dreamPkg != null && !dreamPkg.equals(PKG) && !dreamPkg.equals(homePkg)) out.add(dreamPkg);
        out.addAll(Arrays.asList(Debloat.DEBLOAT));
        if (Config.DISABLE_ASSISTANT) out.addAll(Arrays.asList(Debloat.ASSISTANT));
        return out;
    }

    /** The home screen in use now ("package/activity"): the HOME role holder, among the TV's home screens. */
    private static String currentHome(Adb adb) throws IOException {
        String holder = homeHolder(adb);
        if (holder.isEmpty() || holder.contains(PKG)) return null;
        String list = adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        for (String line : list.split("\n")) {
            line = line.trim();
            if (line.startsWith(holder + "/")) return line;
        }
        return null;
    }

    /**
     * The package that is the home screen now, or "". Some Android 12 TVs have no `cmd role get-role-holders` (it
     * answers "Unknown command"), so then we ask which activity opens HOME.
     */
    private static String homeHolder(Adb adb) throws IOException {
        String holder = adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim();
        if (holder.matches("[\\w.]+(;[\\w.]+)*")) return holder;
        String[] lines = adb.shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").trim().split("\n");
        String component = lines[lines.length - 1].trim();
        // With no default home yet, the system's chooser answers ("android/...ResolverActivity"): unknown.
        return component.contains("/") && !component.startsWith("android/") ? packageOf(component) : "";
    }

    /** Whether Google TV's setup app is one of the home screens (its RecoveryActivity), so it must go off too. */
    private static boolean setupIsHome(Adb adb) throws IOException {
        return adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").contains(GOOGLE_SETUP + "/");
    }

    /** Apps that saved a Wi-Fi network ("cname" in the Wi-Fi service's list of saved networks). */
    private static Set<String> wifiOwners(Adb adb) throws IOException {
        Set<String> owners = new HashSet<>();
        Matcher m = Pattern.compile("cname=([\\w.]+)").matcher(adb.shell("dumpsys wifi | grep -o 'cname=[^ ]*'"));
        while (m.find()) owners.add(m.group(1));
        return owners;
    }

    /** The recorded home screen of the TV, as {@link #realHome}. */
    private static String originalHome(JSONObject s) {
        return realHome(s.optString("originalHome", ""));
    }

    /**
     * Some TVs (TCL, Android 14) leave the HOME role with Google TV's recovery home, while launcherx is the one that
     * answers the Home button. Treat that as launcherx, or it stays on and "original" restores the recovery screen.
     */
    private static String realHome(String home) {
        return home.startsWith(GOOGLE_SETUP + "/") ? GOOGLE_HOME : home;
    }

    /** The system's default screensaver ("package/service"), or "". */
    private static String defaultDream(Adb adb) throws IOException {
        String d = adb.shell("settings get secure screensaver_default_component").trim();
        return d.contains("/") && !d.startsWith(PKG + "/") ? d : "";
    }

    private static String packageOf(String component) {
        if (component == null || component.isEmpty()) return null;
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : component;
    }

    /** Packages the shell sees (all; "-d" = only the disabled ones). The app alone can't see the ones it doesn't declare. */
    private static Set<String> packages(Adb adb, String flags) throws IOException {
        Set<String> out = new HashSet<>();
        for (String line : adb.shell("pm list packages " + flags).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    private static Adb connect(Context c, JSONObject s) throws IOException {
        busy(c, "Подключение…");
        Adb adb = Adb.connect(c, 120_000, () -> busy(c, "Примите на ТВ запрос «Разрешить отладку по USB?» (отметьте «Всегда разрешать»)."));
        if (!s.optBoolean("adbAuthorized")) {
            JSONObject latest = state(c);
            try { latest.put("adbAuthorized", true); } catch (JSONException ignored) {}
            save(c, latest);
        }
        return adb;
    }

    private static void check(String output, String expected, String error) throws IOException {
        if (!output.contains(expected)) throw new IOException(error + " (" + output.trim() + ")");
    }

    // MARK: - Measurements (without opening apps: memory and the home button)

    private static void measureHomeTime(Context c, Adb adb, String mode) throws IOException, JSONException {
        List<Integer> samples = new ArrayList<>();
        Pattern total = Pattern.compile("TotalTime: (\\d+)");
        for (int i = 0; i < 3; i++) {
            adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
            SystemClock.sleep(500);
            Matcher m = total.matcher(adb.shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"));
            if (m.find()) samples.add(Integer.parseInt(m.group(1)));
            SystemClock.sleep(600);
        }
        adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
        if (samples.isEmpty()) return;
        Collections.sort(samples);
        putMetric(c, mode, "homeMs", samples.get(samples.size() / 2));
    }

    /** Two minutes after a switch (the home screen settled, caches trimmed). */
    static void measureMemoryNow(Context c, String mode) {
        synchronized (RUN) {
            if (!mode.equals(state(c).optString("mode"))) return;
            try (Adb adb = Adb.connect(c, 10_000, null)) {
                measureMemory(c, adb, mode);
            } catch (Exception e) {
                AppLog.w("memory measurement failed: " + e.getMessage());
            }
        }
    }

    private static void measureMemory(Context c, Adb adb, String mode) throws IOException, JSONException {
        Matcher avail = Pattern.compile("MemAvailable:\\s+(\\d+) kB").matcher(adb.shell("cat /proc/meminfo"));
        if (avail.find()) putMetric(c, mode, "memAvailableMB", Long.parseLong(avail.group(1)) / 1024);
        long pss = 0;
        String home = packageOf(originalHome(state(c)));
        String[] processes = FREE.equals(mode) ? new String[] {PKG + ":home"}
                : home == null ? new String[0] : new String[] {home, home + ":coreservices"};
        Pattern total = Pattern.compile("TOTAL PSS:\\s+(\\d+)");
        for (String p : processes) {
            Matcher m = total.matcher(adb.shell("dumpsys meminfo " + p));
            if (m.find()) pss += Long.parseLong(m.group(1));
        }
        if (pss > 0) putMetric(c, mode, "homePssMB", pss / 1024);
    }

    private static void putMetric(Context c, String mode, String key, long value) throws JSONException {
        JSONObject s = state(c);
        JSONObject metrics = s.optJSONObject("metrics");
        if (metrics == null) metrics = new JSONObject();
        JSONObject m = metrics.optJSONObject(mode);
        if (m == null) m = new JSONObject();
        m.put(key, value).put("at", System.currentTimeMillis());
        metrics.put(mode, m);
        s.put("metrics", metrics);
        save(c, s);
    }

    private static JSONObject metrics(JSONObject s, String mode) {
        JSONObject metrics = s.optJSONObject("metrics");
        return metrics == null ? null : metrics.optJSONObject(mode);
    }

    // MARK: - State (a file shared by the two processes; atomic writes)

    static JSONObject state(Context c) {
        synchronized (FILE_LOCK) {
            File f = new File(c.getFilesDir(), "home-state.json");
            if (!f.exists()) return defaults();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] data = new byte[(int) f.length()];
                int read = 0;
                while (read < data.length) {
                    int n = in.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            } catch (IOException | JSONException e) {
                return defaults();
            }
        }
    }

    private static JSONObject defaults() {
        try {
            return new JSONObject().put("mode", ORIGINAL);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static void save(Context c, JSONObject s) {
        synchronized (FILE_LOCK) {
            File dir = c.getFilesDir();
            File tmp = new File(dir, "home-state.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(s.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                AppLog.w("could not save the state: " + e.getMessage());
                return;
            }
            if (!tmp.renameTo(new File(dir, "home-state.json"))) AppLog.w("could not replace the state file");
        }
    }

    private static void busy(Context c, String text) {
        JSONObject s = state(c);
        try { s.put("busy", text).put("error", ""); } catch (JSONException ignored) {}
        save(c, s);
    }

    private static void done(Context c, String error) {
        JSONObject s = state(c);
        s.remove("busy");
        try { s.put("error", error == null ? "" : error); } catch (JSONException ignored) {}
        save(c, s);
        if (error != null) {
            AppLog.w(error);
            notice(c, error);
        }
    }

    /** A notice for the home screen (a bubble) and the settings screen. Keeps the last 20. */
    static void notice(Context c, String text) {
        AppLog.w("notice: " + text);
        JSONObject s = state(c);
        JSONArray old = s.optJSONArray("notices");
        JSONArray list = new JSONArray();
        try {
            list.put(new JSONObject().put("at", System.currentTimeMillis()).put("text", text));
            if (old != null) for (int i = 0; i < old.length() && list.length() < 20; i++) list.put(old.get(i));
            s.put("notices", list);
        } catch (JSONException ignored) {}
        save(c, s);
    }

    // MARK: - Helpers

    static List<String> stringList(JSONObject s, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = s.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static List<String> sorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
