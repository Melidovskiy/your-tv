package io.freemytv;

import org.json.JSONObject;

import java.util.List;

public final class ForecastTest {
    public static void main(String[] args) {
        JSONObject daily = new JSONObject("{\"time\":[\"2026-10-20\",\"2026-10-21\",\"2026-10-22\",\"2026-10-23\"],"
                + "\"weather_code\":[3,3,61,0],\"temperature_2m_max\":[9.4,10.1,8,11],\"temperature_2m_min\":[3,4,5,2]}");
        List<Forecast.Day> d = Forecast.days(daily, 1, 3);
        Check.eq(3, d.size(), "three days");
        Check.eq("2026-10-21", d.get(0).date, "first is tomorrow");
        Check.eq(61, d.get(1).code, "code");
        Check.eq(11.0, d.get(2).max, "max");
        Check.eq(2.0, d.get(2).min, "min");
        Check.eq("Завтра", Forecast.label("2026-10-21", "2026-10-20"), "tomorrow");
        Check.eq("Чт", Forecast.label("2026-10-22", "2026-10-20"), "thursday");
        Check.eq("Пт", Forecast.label("2026-10-23", "2026-10-20"), "friday");
        Check.eq("Пт", Forecast.label("2026-01-02", "2025-12-31"), "across the new year");
        JSONObject two = new JSONObject("{\"time\":[\"2026-10-20\",\"2026-10-21\"],\"weather_code\":[3,3],"
                + "\"temperature_2m_max\":[9,10],\"temperature_2m_min\":[3,4]}");
        Check.eq(1, Forecast.days(two, 1, 3).size(), "short answer doesn't crash");
        System.out.println("PASS ForecastTest");
    }
}
