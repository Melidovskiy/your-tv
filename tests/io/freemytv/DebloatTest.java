package io.freemytv;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class DebloatTest {
    public static void main(String[] args) {
        Set<String> d = new HashSet<>(Arrays.asList(Debloat.DEBLOAT));
        Check.eq(Debloat.DEBLOAT.length, d.size(), "no duplicates");
        for (String p : d) Check.yes(!Debloat.PROTECTED.contains(p), "not protected: " + p);
        for (String p : Debloat.ASSISTANT) Check.yes(!Debloat.PROTECTED.contains(p), "assistant not protected: " + p);
        for (String p : new String[] {"com.mitv.tvhome.atv", "com.mitv.tvhome.michannel", "com.miui.tv.analytics", "com.xiaomi.statistic"})
            Check.yes(d.contains(p), "debloats " + p);
        for (String p : new String[] {"com.xiaomi.mitv.updateservice", "com.mediatek.hotkey.dispatcher", "mitv.service",
                "com.mediatek.wwtv.tvcenter", "com.xiaomi.mitv.mediaexplorer"})
            Check.yes(Debloat.PROTECTED.contains(p), "protects " + p);
        System.out.println("PASS DebloatTest");
    }
}
