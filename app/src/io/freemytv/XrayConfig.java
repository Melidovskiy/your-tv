package io.freemytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The Xray core's config for the TV: a TUN inbound (the VPN's file descriptor, handed over by TunService) and a local
 * SOCKS inbound (for this app's own requests that must go through the server, like TMDB), out through the proxy
 * server (ProxyLink); local networks go direct. Pure Java + org.json (test.sh).
 */
final class XrayConfig {
    private XrayConfig() {}

    static final String[] LOCAL_NETS = {"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8"};

    /** `tun`: with the TUN inbound (TunService set one up); without, only the local SOCKS inbound runs. */
    static String build(String link, int socksPort, boolean tun) {
        try {
            JSONObject proxy = ProxyLink.outbound(link);

            JSONObject sniffing = new JSONObject().put("enabled", true).put("routeOnly", true)
                    .put("destOverride", new JSONArray().put("http").put("tls"));
            JSONObject tunIn = new JSONObject().put("tag", "tun").put("protocol", "tun")
                    .put("settings", new JSONObject().put("name", "xray0").put("mtu", 1500))
                    .put("sniffing", sniffing);
            JSONObject socks = new JSONObject().put("tag", "socks").put("protocol", "socks")
                    .put("listen", "127.0.0.1").put("port", socksPort)
                    .put("settings", new JSONObject().put("auth", "noauth").put("udp", true));

            JSONArray inbounds = tun ? new JSONArray().put(tunIn).put(socks) : new JSONArray().put(socks);
            JSONArray rules = new JSONArray()
                    .put(new JSONObject().put("type", "field").put("ip", new JSONArray(LOCAL_NETS)).put("outboundTag", "direct"))
                    // YouTube over QUIC (UDP 443) is blocked so it falls back to TCP, which Vision carries better.
                    .put(new JSONObject().put("type", "field").put("network", "udp").put("port", "443").put("outboundTag", "block"));

            return new JSONObject()
                    .put("log", new JSONObject().put("loglevel", "warning"))
                    .put("dns", new JSONObject().put("servers", new JSONArray().put("1.1.1.1").put("8.8.8.8")))
                    .put("inbounds", inbounds)
                    .put("outbounds", new JSONArray().put(proxy)
                            .put(new JSONObject().put("tag", "direct").put("protocol", "freedom"))
                            .put(new JSONObject().put("tag", "block").put("protocol", "blackhole")))
                    .put("routing", new JSONObject().put("domainStrategy", "AsIs").put("rules", rules))
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
