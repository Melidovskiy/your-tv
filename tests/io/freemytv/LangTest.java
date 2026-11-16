package io.freemytv;

import java.util.Calendar;

public final class LangTest {
    public static void main(String[] args) {
        Lang.set("en");
        Check.eq("Tomorrow", Forecast.label("2026-10-22", "2026-10-21"), "en tomorrow");
        Check.eq("Fri", Forecast.label("2026-10-23", "2026-10-21"), "en weekday");
        Check.eq("1 season", 1 + " " + Lang.plural(1, "сезон", "сезона", "сезонов", "season", "seasons"), "en one");
        Check.eq("seasons", Lang.plural(22, "сезон", "сезона", "сезонов", "season", "seasons"), "en many");
        Check.eq("Sun", Lang.weekdayShort(Calendar.SUNDAY), "en sun");
        Lang.set("ru");
        Check.eq("Завтра", Forecast.label("2026-10-22", "2026-10-21"), "ru tomorrow");
        Check.eq("сезона", Lang.plural(22, "сезон", "сезона", "сезонов", "season", "seasons"), "ru few");
        Lang.set("de");
        Check.eq("ru", Lang.code(), "anything else is Russian");
        System.out.println("PASS LangTest");
    }
}
