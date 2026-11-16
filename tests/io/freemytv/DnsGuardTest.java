package io.freemytv;

public final class DnsGuardTest {
    public static void main(String[] args) {
        Check.yes(DnsGuard.shouldTurnOff("hostname", false, 120_000), "failing 2 min");
        Check.yes(!DnsGuard.shouldTurnOff("hostname", false, 119_999), "not yet");
        Check.yes(!DnsGuard.shouldTurnOff("hostname", true, 999_999), "working");
        Check.yes(!DnsGuard.shouldTurnOff("off", false, 999_999), "not ours");
        Check.yes(!DnsGuard.shouldTurnOff(null, false, 999_999), "unset");
        System.out.println("PASS DnsGuardTest");
    }
}
