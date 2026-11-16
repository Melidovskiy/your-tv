package io.freemytv;

import java.util.Objects;

/** Tiny assertions for the host tests (test.sh): no test framework, only the JDK. */
final class Check {
    private Check() {}

    static void eq(Object want, Object got, String what) {
        if (!Objects.equals(want, got)) throw new AssertionError(what + ": want <" + want + "> got <" + got + ">");
    }

    static void yes(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    /** Runs `body` and expects it to throw `type`. */
    static void throwsIt(Class<? extends Throwable> type, Runnable body, String what) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return;
            throw new AssertionError(what + ": threw " + t + " instead of " + type.getSimpleName());
        }
        throw new AssertionError(what + ": did not throw " + type.getSimpleName());
    }
}
