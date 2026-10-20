package io.freemytv;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A VLESS share link, as 3X-UI and v2rayNG write it:
 * vless://<uuid>@<host>:<port>?security=tls&flow=…&fp=…&alpn=…&sni=…&type=tcp#<name>. Pure Java (test.sh).
 * The link is a secret (it lets anyone use the server): never log it.
 */
final class VlessLink {
    final String uuid, host, security, flow, fingerprint, sni, type, name;
    final int port;
    final List<String> alpn;

    private VlessLink(String uuid, String host, int port, Map<String, String> q, String name) {
        this.uuid = uuid;
        this.host = host;
        this.port = port;
        this.security = q.getOrDefault("security", "none");
        this.flow = q.getOrDefault("flow", "");
        this.fingerprint = q.getOrDefault("fp", "");
        this.sni = q.getOrDefault("sni", "");
        this.type = q.getOrDefault("type", "tcp");
        String a = q.getOrDefault("alpn", "");
        List<String> list = new ArrayList<>();
        for (String one : a.split(",")) if (!one.trim().isEmpty()) list.add(one.trim());
        this.alpn = Collections.unmodifiableList(list);
        this.name = name;
    }

    /** The TLS server name: the link's sni, or the host when it's empty. */
    String serverName() {
        return sni.isEmpty() ? host : sni;
    }

    static VlessLink parse(String link) {
        if (link == null || !link.startsWith("vless://")) throw new IllegalArgumentException("not a vless:// link");
        String rest = link.substring("vless://".length());
        String name = "";
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            name = decode(rest.substring(hash + 1));
            rest = rest.substring(0, hash);
        }
        String query = "";
        int qm = rest.indexOf('?');
        if (qm >= 0) {
            query = rest.substring(qm + 1);
            rest = rest.substring(0, qm);
        }
        int at = rest.lastIndexOf('@');
        if (at <= 0) throw new IllegalArgumentException("no user id");
        String uuid = decode(rest.substring(0, at));
        String hostPort = rest.substring(at + 1);
        int colon = hostPort.lastIndexOf(':');
        if (colon <= 0 || colon == hostPort.length() - 1 || hostPort.endsWith("]")) throw new IllegalArgumentException("no port");
        String host = hostPort.substring(0, colon);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        int port;
        try {
            port = Integer.parseInt(hostPort.substring(colon + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad port");
        }
        if (port < 1 || port > 65535 || host.isEmpty()) throw new IllegalArgumentException("bad host or port");
        Map<String, String> q = new HashMap<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            if (eq < 0) q.put(decode(pair), "");
            else q.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
        }
        return new VlessLink(uuid, host, port, q, name);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            throw new IllegalArgumentException("bad escape in link");
        }
    }
}
