package io.freemytv;

import org.json.JSONArray;
import org.json.JSONObject;

public final class XrayConfigTest {
    public static void main(String[] args) {
        JSONObject c = new JSONObject(XrayConfig.build(ProxyLinkTest.VLESS, 10808, true));
        JSONArray in = c.getJSONArray("inbounds");
        Check.eq("tun", in.getJSONObject(0).getString("protocol"), "tun first");
        Check.eq("socks", in.getJSONObject(1).getString("protocol"), "socks");
        Check.eq("127.0.0.1", in.getJSONObject(1).getString("listen"), "socks local only");
        Check.eq(10808, in.getJSONObject(1).getInt("port"), "socks port");
        JSONObject out = c.getJSONArray("outbounds").getJSONObject(0);
        Check.eq("proxy", out.getString("tag"), "proxy first");
        JSONObject server = out.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0);
        Check.eq("example.org", server.getString("address"), "address");
        Check.eq(443, server.getInt("port"), "port");
        JSONObject user = server.getJSONArray("users").getJSONObject(0);
        Check.eq("vless", out.getString("protocol"), "vless");
        Check.eq(ProxyLinkTest.UUID, user.getString("id"), "id");
        Check.eq("xtls-rprx-vision", user.getString("flow"), "flow");
        Check.eq("none", user.getString("encryption"), "encryption");
        JSONObject stream = out.getJSONObject("streamSettings");
        Check.eq("tcp", stream.getString("network"), "network");
        Check.eq("tls", stream.getString("security"), "security");
        JSONObject tls = stream.getJSONObject("tlsSettings");
        Check.eq("example.org", tls.getString("serverName"), "sni");
        Check.eq("chrome", tls.getString("fingerprint"), "fp");
        Check.eq("http/1.1", tls.getJSONArray("alpn").getString(0), "alpn");
        boolean direct = false;
        JSONArray outs = c.getJSONArray("outbounds");
        for (int i = 0; i < outs.length(); i++) direct |= "direct".equals(outs.getJSONObject(i).getString("tag"));
        Check.yes(direct, "has a direct outbound");
        Check.yes(c.toString().contains("192.168.0.0/16"), "LAN direct");
        JSONArray socksOnly = new JSONObject(XrayConfig.build(ProxyLinkTest.VLESS, 10808, false)).getJSONArray("inbounds");
        Check.eq(1, socksOnly.length(), "without a TUN: one inbound");
        Check.eq("socks", socksOnly.getJSONObject(0).getString("protocol"), "without a TUN: socks only");
        System.out.println("PASS XrayConfigTest");
    }
}
