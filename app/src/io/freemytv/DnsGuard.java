package io.freemytv;

/**
 * When to give up on the ad-blocking DNS. Android's strict Private DNS breaks every app's internet if the DNS server
 * stops answering, so after two minutes of failing it is better to turn it off. Pure Java (test.sh).
 */
final class DnsGuard {
    private DnsGuard() {}

    static final long GRACE_MS = 120_000;

    /** True when our strict mode ("hostname") has been failing for at least GRACE_MS. */
    static boolean shouldTurnOff(String mode, boolean privateDnsActive, long failingForMs) {
        return "hostname".equals(mode) && !privateDnsActive && failingForMs >= GRACE_MS;
    }
}
