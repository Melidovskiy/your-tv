package io.freemytv;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Share links as panels and providers hand them out (3X-UI, Marzban, Hiddify, Outline…), all with fake values. */
public final class ProxyLinkTest {
    static final String UUID = "11111111-2222-3333-4444-555555555555";
    static final String VLESS = "vless://" + UUID + "@example.org:443?alpn=http%2F1.1&encryption=none"
            + "&flow=xtls-rprx-vision&fp=chrome&security=tls&sni=&type=tcp#Test";

    public static void main(String[] args) {
        // VLESS over TCP + TLS (3X-UI's default)
        JSONObject o = ProxyLink.outbound(VLESS);
        Check.eq("vless", o.getString("protocol"), "vless protocol");
        Check.eq("proxy", o.getString("tag"), "tag");
        JSONObject server = o.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0);
        Check.eq("example.org", server.getString("address"), "address");
        Check.eq(443, server.getInt("port"), "port");
        JSONObject user = server.getJSONArray("users").getJSONObject(0);
        Check.eq(UUID, user.getString("id"), "id");
        Check.eq("xtls-rprx-vision", user.getString("flow"), "flow");
        Check.eq("none", user.getString("encryption"), "encryption");
        JSONObject stream = o.getJSONObject("streamSettings");
        Check.eq("tcp", stream.getString("network"), "network");
        Check.eq("tls", stream.getString("security"), "security");
        JSONObject tls = stream.getJSONObject("tlsSettings");
        Check.eq("example.org", tls.getString("serverName"), "empty sni falls back to host");
        Check.eq("chrome", tls.getString("fingerprint"), "fp");
        Check.eq("http/1.1", tls.getJSONArray("alpn").getString(0), "alpn");
        Check.eq("cdn.example.net", ProxyLink.outbound(VLESS.replace("sni=", "sni=cdn.example.net"))
                .getJSONObject("streamSettings").getJSONObject("tlsSettings").getString("serverName"), "sni");

        // VLESS + Reality (Marzban, Remnawave, 3X-UI "reality")
        JSONObject reality = ProxyLink.outbound("vless://" + UUID + "@1.2.3.4:443?type=tcp&security=reality"
                + "&pbk=PUBKEY_x-1&sid=ab12&spx=%2F&fp=firefox&sni=www.microsoft.com&flow=xtls-rprx-vision#R")
                .getJSONObject("streamSettings");
        Check.eq("reality", reality.getString("security"), "reality");
        JSONObject rs = reality.getJSONObject("realitySettings");
        Check.eq("PUBKEY_x-1", rs.getString("publicKey"), "pbk");
        Check.eq("ab12", rs.getString("shortId"), "sid");
        Check.eq("/", rs.getString("spiderX"), "spx");
        Check.eq("www.microsoft.com", rs.getString("serverName"), "reality sni");
        Check.eq("firefox", rs.getString("fingerprint"), "reality fp");

        // VLESS over WebSocket + TLS, gRPC, xhttp
        JSONObject ws = ProxyLink.outbound("vless://" + UUID + "@example.org:443?type=ws&security=tls"
                + "&path=%2Fws%3Fed%3D2048&host=cdn.example.org#W").getJSONObject("streamSettings");
        Check.eq("ws", ws.getString("network"), "ws");
        Check.eq("/ws?ed=2048", ws.getJSONObject("wsSettings").getString("path"), "ws path");
        Check.eq("cdn.example.org", ws.getJSONObject("wsSettings").getString("host"), "ws host");
        Check.eq("cdn.example.org", ws.getJSONObject("tlsSettings").getString("serverName"), "sni falls back to ws host");
        JSONObject grpc = ProxyLink.outbound("vless://" + UUID + "@example.org:443?type=grpc&security=tls"
                + "&serviceName=svc&mode=multi").getJSONObject("streamSettings");
        Check.eq("svc", grpc.getJSONObject("grpcSettings").getString("serviceName"), "grpc service");
        Check.yes(grpc.getJSONObject("grpcSettings").getBoolean("multiMode"), "grpc multi");
        JSONObject xhttp = ProxyLink.outbound("vless://" + UUID + "@example.org:443?type=xhttp&security=tls"
                + "&path=%2Fx&mode=auto").getJSONObject("streamSettings");
        Check.eq("xhttp", xhttp.getString("network"), "xhttp");
        Check.eq("/x", xhttp.getJSONObject("xhttpSettings").getString("path"), "xhttp path");

        // VMess (base64 JSON, v2rayN format)
        String vmessJson = "{\"v\":\"2\",\"ps\":\"V\",\"add\":\"vm.example.org\",\"port\":\"8443\",\"id\":\"" + UUID
                + "\",\"aid\":\"0\",\"scy\":\"auto\",\"net\":\"ws\",\"host\":\"vm.example.org\",\"path\":\"/v\",\"tls\":\"tls\"}";
        JSONObject vmess = ProxyLink.outbound("vmess://" + b64(vmessJson));
        Check.eq("vmess", vmess.getString("protocol"), "vmess");
        JSONObject vs = vmess.getJSONObject("settings").getJSONArray("vnext").getJSONObject(0);
        Check.eq("vm.example.org", vs.getString("address"), "vmess address");
        Check.eq(8443, vs.getInt("port"), "vmess port as string");
        Check.eq(UUID, vs.getJSONArray("users").getJSONObject(0).getString("id"), "vmess id");
        Check.eq("ws", vmess.getJSONObject("streamSettings").getString("network"), "vmess ws");
        Check.eq("tls", vmess.getJSONObject("streamSettings").getString("security"), "vmess tls");

        // Trojan
        JSONObject trojan = ProxyLink.outbound("trojan://p%40ss@tr.example.org:443?security=tls&sni=tr.example.org&type=grpc&serviceName=t#T");
        Check.eq("trojan", trojan.getString("protocol"), "trojan");
        JSONObject ts = trojan.getJSONObject("settings").getJSONArray("servers").getJSONObject(0);
        Check.eq("p@ss", ts.getString("password"), "trojan password");
        Check.eq("tls", trojan.getJSONObject("streamSettings").getString("security"), "trojan defaults to tls");

        // Shadowsocks: SIP002 (Outline) and the older all-base64 form
        JSONObject ss = ProxyLink.outbound("ss://" + b64("chacha20-ietf-poly1305:secret") + "@ss.example.org:8388#S");
        JSONObject s1 = ss.getJSONObject("settings").getJSONArray("servers").getJSONObject(0);
        Check.eq("shadowsocks", ss.getString("protocol"), "ss");
        Check.eq("chacha20-ietf-poly1305", s1.getString("method"), "ss method");
        Check.eq("secret", s1.getString("password"), "ss password");
        Check.eq(8388, s1.getInt("port"), "ss port");
        JSONObject old = ProxyLink.outbound("ss://" + b64("aes-256-gcm:pw@1.2.3.4:443") + "#Old");
        Check.eq("1.2.3.4", old.getJSONObject("settings").getJSONArray("servers").getJSONObject(0).getString("address"), "old ss");

        // Rejected
        for (String bad : new String[] {"hysteria2://x@h:1", "vless://@example.org:443?security=tls",
                "vless://" + UUID + "@example.org?security=tls", "not a link", "", "vless://" + UUID + "@example.org:99999",
                "vmess://not-base64!", "ss://bm9wZQ@h:1"}) {
            Check.throwsIt(IllegalArgumentException.class, () -> ProxyLink.outbound(bad), "rejects " + bad);
        }
        Check.throwsIt(IllegalArgumentException.class, () -> ProxyLink.outbound(null), "rejects null");
        System.out.println("PASS ProxyLinkTest");
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
