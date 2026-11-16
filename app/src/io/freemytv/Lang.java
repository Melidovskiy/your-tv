package io.freemytv;

import java.util.Locale;

/**
 * The interface language: Russian or English (Home Settings; by default the TV's own language). Every on-screen text
 * is written in place as tr("…", "…"). Pure Java (test.sh).
 */
final class Lang {
    private Lang() {}

    private static volatile boolean en;
    private static final String[] WEEKDAYS_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    /** "ru" or "en". */
    static void set(String language) { en = "en".equals(language); }

    static boolean english() { return en; }

    /** "ru" or "en" (TMDB, the formatted-value cache). */
    static String code() { return en ? "en" : "ru"; }

    static Locale locale() { return en ? Locale.UK : Ru.LOCALE; }

    static String tr(String ru, String english) { return en ? english : ru; }

    /** Russian's three forms, or English's two. */
    static String plural(int n, String one, String few, String many, String enOne, String enMany) {
        return en ? (Math.abs(n) == 1 ? enOne : enMany) : Ru.plural(n, one, few, many);
    }

    /** "Пн"/"Mon" for Calendar.SUNDAY (1) .. Calendar.SATURDAY (7). */
    static String weekdayShort(int calendarDay) {
        return en ? WEEKDAYS_EN[(calendarDay - 1 + 7) % 7] : Ru.weekdayShort(calendarDay);
    }
}
