package io.freemytv;

import java.util.Calendar;

public final class RuTest {
    public static void main(String[] args) {
        Check.eq("сезон", Ru.plural(1, "сезон", "сезона", "сезонов"), "1");
        Check.eq("сезона", Ru.plural(2, "сезон", "сезона", "сезонов"), "2");
        Check.eq("сезонов", Ru.plural(5, "сезон", "сезона", "сезонов"), "5");
        Check.eq("сезонов", Ru.plural(11, "сезон", "сезона", "сезонов"), "11");
        Check.eq("сезон", Ru.plural(21, "сезон", "сезона", "сезонов"), "21");
        Check.eq("сезона", Ru.plural(22, "сезон", "сезона", "сезонов"), "22");
        Check.eq("сезонов", Ru.plural(112, "сезон", "сезона", "сезонов"), "112");
        Check.eq("сезонов", Ru.plural(0, "сезон", "сезона", "сезонов"), "0");
        Check.eq("Чт", Ru.weekdayShort(Calendar.THURSDAY), "thu");
        Check.eq("Вс", Ru.weekdayShort(Calendar.SUNDAY), "sun");
        System.out.println("PASS RuTest");
    }
}
