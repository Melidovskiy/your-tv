package io.freemytv;

import java.util.Calendar;
import java.util.Locale;

/** Russian grammar the UI needs: plural forms and short weekday names. Pure Java (tested on the PC by test.sh). */
final class Ru {
    private Ru() {}

    static final Locale LOCALE = Locale.forLanguageTag("ru-RU");
    private static final String[] WEEKDAYS = {"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};

    /** The form for `n`: 1, 21, 31… → one; 2–4, 22–24… → few; everything else (incl. 11–14) → many. */
    static String plural(int n, String one, String few, String many) {
        int a = Math.abs(n), tens = a % 100, units = a % 10;
        if (tens >= 11 && tens <= 14) return many;
        if (units == 1) return one;
        if (units >= 2 && units <= 4) return few;
        return many;
    }

    /** "Пн".."Вс" for Calendar.SUNDAY (1) .. Calendar.SATURDAY (7). */
    static String weekdayShort(int calendarDay) {
        return WEEKDAYS[(calendarDay - Calendar.SUNDAY + 7) % 7];
    }
}
