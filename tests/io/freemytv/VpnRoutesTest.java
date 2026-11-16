package io.freemytv;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VpnRoutesTest {
    public static void main(String[] args) {
        Check.eq(Arrays.asList("org.smarttube.stable"),
                VpnRoutes.allowed(set("org.smarttube.stable", "tv.okko.androidtv")), "only youtube apps");
        Check.eq(Collections.emptyList(), VpnRoutes.allowed(set("tv.okko.androidtv")), "none installed → empty");
        Check.eq(Arrays.asList(VpnRoutes.YOUTUBE_APPS), VpnRoutes.allowed(set(VpnRoutes.YOUTUBE_APPS)), "all three, in order");
        Check.eq(Arrays.asList("io.gh.reisxd.tizentube.cobalt"),
                VpnRoutes.allowed(set("io.gh.reisxd.tizentube.cobalt", "ru.rutube.app")), "TizenTube");
        System.out.println("PASS VpnRoutesTest");
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }
}
