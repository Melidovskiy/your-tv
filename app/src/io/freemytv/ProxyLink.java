package io.freemytv;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * A proxy share link, as panels and providers hand them out (3X-UI, Marzban, Hiddify, Outline…), turned into Xray's
 * "proxy" outbound: vless:// (TCP, WebSocket, gRPC, HTTPUpgrade, xhttp; TLS or Reality), vmess:// (v2rayN's base64
 * JSON), trojan:// and ss:// (SIP002 or the older all-base64 form). Pure Java + org.json (test.sh).
 * A link is a secret (it lets anyone use the server): never log it.
 */
final class ProxyLink {
    private ProxyLink() {}

    static JSONObject outbound(String link) {
        if (link == null) throw new IllegalArgumentException("no link");
        link = link.trim();
        int colon = link.indexOf("://");
        if (colon <= 0) throw new IllegalArgumentException("not a link");
        try {
            switch (link.substring(0, colon).toLowerCase(java.util.Locale.ROOT)) {
                case "vless": return vless(link);
                case "vmess": return vmess(link);
                case "trojan": return trojan(link);
                case "ss": return shadowsocks(link);
                default: throw new IllegalArgumentException("unsupported link type");
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("bad link");
        }
    }

    // MARK: - Protocols

    private static JSONObject vless(String link) throws JSONException {
        Url u = Url.parse(link);
        if (u.user.isEmpty()) throw new IllegalArgumentException("no user id");
        JSONObject user = new JSONObject().put("id", u.user).put("encryption", u.q("encryption", "none"));
        if (!u.q("flow", "").isEmpty()) user.put("flow", u.q("flow", ""));
        return outbound("vless", vnext(u.host, u.port, user), stream(u.query, u.host, "none"));
    }

    private static JSONObject trojan(String link) throws JSONException {
        Url u = Url.parse(link);
        if (u.user.isEmpty()) throw new IllegalArgumentException("no password");
        JSONObject server = new JSONObject().put("address", u.host).put("port", u.port).put("password", u.user);
        return outbound("trojan", new JSONObject().put("servers", new JSONArray().put(server)), stream(u.query, u.host, "tls"));
    }

    private static JSONObject vmess(String link) throws JSONException {
        JSONObject v;
        try {
            v = new JSONObject(base64(link.substring("vmess://".length())));
        } catch (JSONException e) {
            throw new IllegalArgumentException("bad vmess link");
        }
        String host = v.optString("add", "");
        int port = port(v.optString("port", ""));
        String id = v.optString("id", "");
        if (host.isEmpty() || id.isEmpty()) throw new IllegalArgumentException("bad vmess link");
        JSONObject user = new JSONObject().put("id", id).put("alterId", parseInt(v.optString("aid", "0"), 0))
                .put("security", v.optString("scy", "auto").isEmpty() ? "auto" : v.optString("scy"));
        Map<String, String> q = new HashMap<>();
        q.put("type", v.optString("net", "tcp"));
        q.put("security", v.optString("tls", ""));
        for (String k : new String[] {"host", "path", "sni", "alpn", "fp"}) q.put(k, v.optString(k, ""));
        q.put("serviceName", v.optString("path", ""));
        q.put("headerType", v.optString("type", ""));
        return outbound("vmess", vnext(host, port, user), stream(q, host, "none"));
    }

    private static JSONObject shadowsocks(String link) throws JSONException {
        String rest = link.substring("ss://".length());
        int hash = rest.indexOf('#');
        if (hash >= 0) rest = rest.substring(0, hash);
        int q = rest.indexOf('?');
        if (q >= 0) rest = rest.substring(0, q);
        if (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
        String userInfo, hostPort;
        int at = rest.lastIndexOf('@');
        if (at >= 0) {                                  // SIP002: ss://base64(method:password)@host:port
            userInfo = rest.substring(0, at);
            userInfo = userInfo.contains(":") ? decode(userInfo) : base64(userInfo);
            hostPort = rest.substring(at + 1);
        } else {                                        // older: ss://base64(method:password@host:port)
            String all = base64(rest);
            int a = all.lastIndexOf('@');
            if (a < 0) throw new IllegalArgumentException("bad ss link");
            userInfo = all.substring(0, a);
            hostPort = all.substring(a + 1);
        }
        int c = userInfo.indexOf(':');
        if (c <= 0 || c == userInfo.length() - 1) throw new IllegalArgumentException("bad ss link");
        String[] hp = hostPort(hostPort);
        JSONObject server = new JSONObject().put("address", hp[0]).put("port", port(hp[1]))
                .put("method", userInfo.substring(0, c)).put("password", userInfo.substring(c + 1));
        return new JSONObject().put("tag", "proxy").put("protocol", "shadowsocks")
                .put("settings", new JSONObject().put("servers", new JSONArray().put(server)));
    }

    // MARK: - Pieces

    private static JSONObject outbound(String protocol, JSONObject settings, JSONObject stream) throws JSONException {
        return new JSONObject().put("tag", "proxy").put("protocol", protocol).put("settings", settings)
                .put("streamSettings", stream);
    }

    private static JSONObject vnext(String host, int port, JSONObject user) throws JSONException {
        JSONObject server = new JSONObject().put("address", host).put("port", port).put("users", new JSONArray().put(user));
        return new JSONObject().put("vnext", new JSONArray().put(server));
    }

    /** streamSettings from the link's parameters (type, security, sni, fp, alpn, pbk, sid, spx, path, host…). */
    private static JSONObject stream(Map<String, String> q, String address, String defaultSecurity) throws JSONException {
        String network = get(q, "type", "tcp");
        if (network.equals("raw")) network = "tcp";
        String security = get(q, "security", defaultSecurity);
        String host = get(q, "host", "");
        String sni = get(q, "sni", host.isEmpty() ? address : host);
        JSONObject s = new JSONObject().put("network", network).put("security", security);
        if (security.equals("tls")) {
            JSONObject tls = new JSONObject().put("serverName", sni);
            if (!get(q, "fp", "").isEmpty()) tls.put("fingerprint", get(q, "fp", ""));
            JSONArray alpn = new JSONArray();
            for (String a : get(q, "alpn", "").split(",")) if (!a.trim().isEmpty()) alpn.put(a.trim());
            if (alpn.length() > 0) tls.put("alpn", alpn);
            if ("1".equals(get(q, "allowInsecure", "")) || "true".equals(get(q, "allowInsecure", ""))) tls.put("allowInsecure", true);
            s.put("tlsSettings", tls);
        } else if (security.equals("reality")) {
            String pbk = get(q, "pbk", "");
            if (pbk.isEmpty()) throw new IllegalArgumentException("reality link without pbk");
            s.put("realitySettings", new JSONObject().put("serverName", sni).put("fingerprint", get(q, "fp", "chrome"))
                    .put("publicKey", pbk).put("shortId", get(q, "sid", "")).put("spiderX", get(q, "spx", "")));
        }
        String path = get(q, "path", "");
        switch (network) {
            case "ws":
                s.put("wsSettings", new JSONObject().put("path", path.isEmpty() ? "/" : path).put("host", host));
                break;
            case "grpc":
                s.put("grpcSettings", new JSONObject().put("serviceName", get(q, "serviceName", path))
                        .put("multiMode", "multi".equals(get(q, "mode", ""))));
                break;
            case "httpupgrade":
                s.put("httpupgradeSettings", new JSONObject().put("path", path.isEmpty() ? "/" : path).put("host", host));
                break;
            case "xhttp":
            case "splithttp":
                s.put("network", "xhttp");
                s.put("xhttpSettings", new JSONObject().put("path", path.isEmpty() ? "/" : path).put("host", host)
                        .put("mode", get(q, "mode", "auto")));
                break;
            case "tcp":
                if ("http".equals(get(q, "headerType", ""))) {
                    JSONObject request = new JSONObject().put("path", new JSONArray().put(path.isEmpty() ? "/" : path));
                    if (!host.isEmpty()) request.put("headers", new JSONObject().put("Host", new JSONArray().put(host)));
                    s.put("tcpSettings", new JSONObject().put("header", new JSONObject().put("type", "http").put("request", request)));
                }
                break;
            default:
                throw new IllegalArgumentException("unsupported transport " + network);
        }
        return s;
    }

    private static String get(Map<String, String> q, String key, String fallback) {
        String v = q.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    /** scheme://user@host:port?query#name, with percent-decoding. */
    private static final class Url {
        String user, host;
        int port;
        final Map<String, String> query = new HashMap<>();

        String q(String key, String fallback) {
            return get(query, key, fallback);
        }

        static Url parse(String link) {
            String rest = link.substring(link.indexOf("://") + 3);
            int hash = rest.indexOf('#');
            if (hash >= 0) rest = rest.substring(0, hash);
            Url u = new Url();
            int qm = rest.indexOf('?');
            if (qm >= 0) {
                for (String pair : rest.substring(qm + 1).split("&")) {
                    if (pair.isEmpty()) continue;
                    int eq = pair.indexOf('=');
                    if (eq < 0) u.query.put(decode(pair), "");
                    else u.query.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
                }
                rest = rest.substring(0, qm);
            }
            if (rest.endsWith("/")) rest = rest.substring(0, rest.length() - 1);
            int at = rest.lastIndexOf('@');
            if (at < 0) throw new IllegalArgumentException("no user");
            u.user = decode(rest.substring(0, at));
            String[] hp = hostPort(rest.substring(at + 1));
            u.host = hp[0];
            u.port = port(hp[1]);
            return u;
        }
    }

    /** "host:port" or "[v6]:port" → {host, port}. */
    private static String[] hostPort(String s) {
        int colon = s.lastIndexOf(':');
        if (colon <= 0 || colon == s.length() - 1 || s.endsWith("]")) throw new IllegalArgumentException("no port");
        String host = s.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        if (host.isEmpty()) throw new IllegalArgumentException("no host");
        return new String[] {host, s.substring(colon + 1)};
    }

    private static int port(String s) {
        int p = parseInt(s, -1);
        if (p < 1 || p > 65535) throw new IllegalArgumentException("bad port");
        return p;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Standard or URL-safe base64, with or without padding. */
    private static String base64(String s) {
        String t = s.trim().replace('-', '+').replace('_', '/');
        while (t.length() % 4 != 0) t += "=";
        try {
            return new String(Base64.getDecoder().decode(t), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad base64");
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("bad escape in link");
        }
    }
}
