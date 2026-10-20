package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** The next days of Open-Meteo's daily forecast, and their labels ("Завтра", "Чт"). Pure Java + org.json (test.sh). */
final class Forecast {
    private Forecast() {}

    static final class Day {
        String date;        // "2026-10-21"
        int code;           // WMO weather code
        double max, min;
    }

    /** `count` days starting at index `from` of the "daily" arrays; fewer when the answer is shorter. */
    static List<Day> days(JSONObject daily, int from, int count) {
        JSONArray time = daily.optJSONArray("time"), codes = daily.optJSONArray("weather_code");
        JSONArray max = daily.optJSONArray("temperature_2m_max"), min = daily.optJSONArray("temperature_2m_min");
        List<Day> out = new ArrayList<>();
        if (time == null || codes == null || max == null || min == null) return out;
        int n = Math.min(Math.min(time.length(), codes.length()), Math.min(max.length(), min.length()));
        for (int i = from; i < n && out.size() < count; i++) {
            Day d = new Day();
            d.date = time.optString(i);
            d.code = codes.optInt(i, -1);
            d.max = max.optDouble(i);
            d.min = min.optDouble(i);
            out.add(d);
        }
        return out;
    }

    /** "Завтра" for the day after `todayIso`, otherwise the short weekday. Both dates as "yyyy-MM-dd". */
    static String label(String isoDate, String todayIso) {
        Calendar day = parse(isoDate), next = parse(todayIso);
        next.add(Calendar.DAY_OF_MONTH, 1);
        if (day.get(Calendar.YEAR) == next.get(Calendar.YEAR) && day.get(Calendar.DAY_OF_YEAR) == next.get(Calendar.DAY_OF_YEAR)) {
            return "Завтра";
        }
        return Ru.weekdayShort(day.get(Calendar.DAY_OF_WEEK));
    }

    private static Calendar parse(String iso) {
        String[] p = iso.split("-");
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
        return c;
    }
}
