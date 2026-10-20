package io.freemytv;

import java.util.Arrays;

public final class VlessLinkTest {
    static final String UUID = "11111111-2222-3333-4444-555555555555";
    static final String LINK = "vless://" + UUID + "@example.org:443?alpn=http%2F1.1&encryption=none"
            + "&flow=xtls-rprx-vision&fp=chrome&security=tls&sni=&type=tcp#Test";

    public static void main(String[] args) {
        VlessLink l = VlessLink.parse(LINK);
        Check.eq(UUID, l.uuid, "uuid");
        Check.eq("example.org", l.host, "host");
        Check.eq(443, l.port, "port");
        Check.eq("xtls-rprx-vision", l.flow, "flow");
        Check.eq("chrome", l.fingerprint, "fp");
        Check.eq(Arrays.asList("http/1.1"), l.alpn, "alpn");
        Check.eq("tls", l.security, "security");
        Check.eq("tcp", l.type, "type");
        Check.eq("example.org", l.serverName(), "empty sni falls back to host");
        Check.eq("Test", l.name, "name");
        Check.eq("cdn.example.net", VlessLink.parse(LINK.replace("sni=", "sni=cdn.example.net")).serverName(), "sni");
        Check.eq(Arrays.asList("h2", "http/1.1"), VlessLink.parse(LINK.replace("alpn=http%2F1.1", "alpn=h2%2Chttp%2F1.1")).alpn, "two alpn");
        for (String bad : new String[] {"vmess://abc@example.org:443", "vless://@example.org:443?security=tls",
                "vless://" + UUID + "@example.org?security=tls", "not a link", "", "vless://" + UUID + "@example.org:99999"}) {
            Check.throwsIt(IllegalArgumentException.class, () -> VlessLink.parse(bad), "rejects " + bad);
        }
        Check.throwsIt(IllegalArgumentException.class, () -> VlessLink.parse(null), "rejects null");
        System.out.println("PASS VlessLinkTest");
    }
}
