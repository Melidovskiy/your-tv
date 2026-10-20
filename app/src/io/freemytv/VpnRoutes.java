package io.freemytv;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Which apps always go through the VPN: the YouTube apps. Pure Java (test.sh). */
final class VpnRoutes {
    private VpnRoutes() {}

    /** TizenTube (YouTube's own TV interface, without ads), SmartTube (any build) and the stock YouTube app. */
    static final String[] YOUTUBE_APPS = {"io.gh.reisxd.tizentube.cobalt", "org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv",
            "com.google.android.youtube.tv"};

    /**
     * The installed ones, in YOUTUBE_APPS order. Can be empty: then no TUN may be set up, because a VPN with an empty
     * allowed list carries every app on the TV.
     */
    static List<String> allowed(Set<String> installed) {
        List<String> out = new ArrayList<>();
        for (String pkg : YOUTUBE_APPS) if (installed.contains(pkg)) out.add(pkg);
        return out;
    }
}
