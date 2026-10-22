package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weather, sunrise and sunset (Open-Meteo, every 30 min), cached so the screen opens with numbers. Only fetched
 * while the home screen or the screensaver is visible (the screen asks). No key needed; the location comes from
 * config.properties.
 */
final class HomeData {
    private static final String WEATHER_QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,weather_code"
            + "&hourly=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=4";

    private static String weatherPlace() {
        return Prefs.latitude() + "," + Prefs.longitude();
    }

    private static String weatherUrl() {
        return "https://api.open-meteo.com/v1/forecast?latitude=" + Prefs.latitude() + "&longitude=" + Prefs.longitude() + WEATHER_QUERY;
    }
    private static final long WEATHER_EVERY = 30 * 60_000L;
    interface Listener { void onData(); }

    /** Settings changed (city): fetch again now. */
    void settingsChanged() {
        weatherAt = 0;
    }

    static final class Weather {
        double temp = Double.NaN, max = Double.NaN, min = Double.NaN;
        int code = -1;
        boolean isDay = true;
        long sunrise, sunset;   // today's, in epoch ms
        // Next hours (starting at the next full hour): time, temperature, code and day/night.
        final List<Long> hours = new ArrayList<>();
        final List<Double> hourTemp = new ArrayList<>();
        final List<Integer> hourCode = new ArrayList<>();
        final List<Boolean> hourDay = new ArrayList<>();
        // The next 3 days (tomorrow first), and today's date to label them.
        List<Forecast.Day> days = new ArrayList<>();
        String today = "";
    }

    private static HomeData instance;

    static synchronized HomeData get(Context c) {
        if (instance == null) instance = new HomeData(c.getApplicationContext());
        return instance;
    }

    volatile long weatherAt;
    volatile Weather weather = new Weather();

    private final SharedPreferences cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private volatile boolean fetchingWeather;

    private HomeData(Context c) {
        cache = c.getSharedPreferences("homedata", Context.MODE_PRIVATE);
        try {
            String w = cache.getString("weather", null);
            // The cached forecast is for the city it was fetched for; another city means fetch again.
            if (w != null && weatherPlace().equals(cache.getString("weatherPlace", ""))) {
                weather = parseWeather(w);
                weatherAt = cache.getLong("weatherAt", 0);
            }
        } catch (Exception ignored) {}
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    /** Fetches whatever is stale. The screen calls this when it appears and every minute while it's visible. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        // A negative age (the TV's clock went back) counts as stale: otherwise the data would freeze.
        if (!fetchingWeather && (now - weatherAt > WEATHER_EVERY || now < weatherAt)) {
            fetchingWeather = true;
            io.execute(() -> {
                try {
                    String body = get(weatherUrl());
                    weather = parseWeather(body);
                    weatherAt = System.currentTimeMillis();
                    cache.edit().putString("weather", body).putLong("weatherAt", weatherAt).putString("weatherPlace", weatherPlace()).apply();
                    notifyListeners();
                } catch (Exception e) {
                    AppLog.w("weather: " + e.getMessage());
                } finally {
                    fetchingWeather = false;
                }
            });
        }
    }

    private void notifyListeners() {
        main.post(() -> { for (Listener l : new ArrayList<>(listeners)) l.onData(); });
    }

    private static Weather parseWeather(String body) throws Exception {
        JSONObject o = new JSONObject(body);
        TimeZone zone = TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID()));
        Weather w = new Weather();
        JSONObject current = o.getJSONObject("current");
        w.temp = current.getDouble("temperature_2m");
        w.code = current.getInt("weather_code");
        w.isDay = current.optInt("is_day", 1) == 1;
        JSONObject daily = o.getJSONObject("daily");
        w.max = daily.getJSONArray("temperature_2m_max").getDouble(0);
        w.min = daily.getJSONArray("temperature_2m_min").getDouble(0);
        w.sunrise = localTime(daily.getJSONArray("sunrise").getString(0), zone);
        w.sunset = localTime(daily.getJSONArray("sunset").getString(0), zone);
        w.today = daily.getJSONArray("time").getString(0);
        w.days = Forecast.days(daily, 1, 3);
        JSONObject hourly = o.optJSONObject("hourly");
        if (hourly != null) {
            JSONArray times = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray codes = hourly.getJSONArray("weather_code");
            JSONArray days = hourly.optJSONArray("is_day");
            long now = System.currentTimeMillis();
            for (int i = 0; i < times.length() && w.hours.size() < 6; i++) {
                long at = localTime(times.getString(i), zone);
                if (at <= now) continue;
                w.hours.add(at);
                w.hourTemp.add(temps.getDouble(i));
                w.hourCode.add(codes.getInt(i));
                w.hourDay.add(days == null || days.optInt(i, 1) == 1);
            }
        }
        return w;
    }

    private static long localTime(String iso, TimeZone zone) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(zone);
        return f.parse(iso).getTime();
    }

    private static String get(String url) throws IOException {
        try {
            return get(url, false);
        } catch (IOException direct) {
            if (!Vpn.configured()) throw direct;
            // Open-Meteo is sometimes unreachable from Russian networks: then through the VPN server.
            return get(url, true);
        }
    }

    private static String get(String url, boolean viaVpn) throws IOException {
        HttpURLConnection c = Net.open(url, viaVpn);
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "FreeMyTV");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /** The night look: always with the dark theme, from evening to morning with "Auto", never with the light one. */
    boolean isNight(long now) {
        String theme = Prefs.theme();
        if (theme.equals("dark")) return true;
        if (theme.equals("light")) return false;
        return isEvening(now);
    }

    /**
     * Evening at your location: from sunset, but no later than 20:00 (a summer sunset at 21:15 is too late for a
     * living room), until sunrise, but no earlier than 7:00. Uses the last weather fetched (it moves ~1 min a day);
     * without data, 18:00 to 7:00. Drives the evening look and the evening wallpapers.
     */
    boolean isEvening(long now) {
        long hour = 60 * 60_000L;
        long t = Shaders.timeOfDay(now, 0);
        long rise = Math.max(Shaders.timeOfDay(weather.sunrise, 7 * hour), 7 * hour);
        long set = Math.min(Shaders.timeOfDay(weather.sunset, 18 * hour), 20 * hour);
        return t < rise || t >= set;
    }

    /** Night: from 21:00 until the evening ends in the morning. Drives the night wallpapers (stars, northern lights). */
    boolean isLateNight(long now) {
        long hour = 60 * 60_000L;
        long t = Shaders.timeOfDay(now, 0);
        return t >= 21 * hour || (t < 12 * hour && isEvening(now));
    }

    // MARK: - Formatting

    private static Locale locale() {
        return Ui.locale();
    }

    /** The weather in words, from Open-Meteo's WMO code. */
    static String condition(int code, boolean day) {
        if (code < 0) return "";
        if (code == 0) return day ? Lang.tr("Ясно", "Clear") : Lang.tr("Ясная ночь", "Clear night");
        if (code == 1) return Lang.tr("Малооблачно", "Mostly clear");
        if (code == 2) return Lang.tr("Переменная облачность", "Partly cloudy");
        if (code == 3) return Lang.tr("Облачно", "Cloudy");
        if (code == 45 || code == 48) return Lang.tr("Туман", "Fog");
        if (code >= 51 && code <= 57) return Lang.tr("Морось", "Drizzle");
        if (code == 65 || code == 67 || code == 82) return Lang.tr("Сильный дождь", "Heavy rain");
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Lang.tr("Снег", "Snow");
        if (code >= 95) return Lang.tr("Гроза", "Thunderstorm");
        return Lang.tr("Дождь", "Rain");
    }

    /** Celsius or Fahrenheit (config.properties "temperature": C, F, or auto = Fahrenheit in the US). */
    static String degrees(double v) {
        if (Double.isNaN(v)) return "—";
        return Math.round(fahrenheit() ? v * 9 / 5 + 32 : v) + "°";
    }

    private static boolean fahrenheit() {
        String unit = Prefs.temperature();
        if (unit.equals("F")) return true;
        if (unit.equals("C")) return false;
        return "US".equals(Locale.getDefault().getCountry());
    }

    static String hour(long at) {
        return Ui.once("hour", at, () -> new SimpleDateFormat(Prefs.twelveHour() ? "h:mm" : "HH:mm", Ui.locale()).format(new java.util.Date(at)));
    }

    /** How long ago the data was fetched, if it's stale (no internet): "12 мин назад". */
    static String staleness(long at, long every) {
        if (at == 0) return "";
        long age = System.currentTimeMillis() - at;
        if (age < every * 2) return "";
        long minutes = age / 60_000;
        return minutes < 60 ? minutes + Lang.tr(" мин назад", " min ago") : (minutes / 60) + Lang.tr(" ч назад", " h ago");
    }
}
