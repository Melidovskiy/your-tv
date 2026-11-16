package io.freemytv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/** Network helpers: HTTP connections (direct or through the VPN server) and the network's name for the home screen. */
final class Net {
    private Net() {}

    private static final Proxy VPN = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", Vpn.SOCKS_PORT));

    /**
     * An HTTP connection; with `viaVpn`, through Xray's local SOCKS port (to your own server), for services blocked
     * in Russia such as TMDB. 8 s timeouts.
     */
    static HttpURLConnection open(String url, boolean viaVpn) throws IOException {
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection) (viaVpn ? u.openConnection(VPN) : u.openConnection());
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        return c;
    }

    /** The Wi-Fi's name; "Подключено" when Android 9 hides it (no location access); "Кабель"; "Нет сети". */
    static String networkLabel(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network n = cm == null ? null : cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null) return Lang.tr("Нет сети", "No network");
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return Lang.tr("Кабель", "Cable");
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Lang.tr("Подключено", "Connected");
        WifiManager wifi = c.getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
        String ssid = info == null ? null : info.getSSID();
        if (ssid == null || ssid.isEmpty() || ssid.equals(WifiManager.UNKNOWN_SSID) || ssid.equals("<unknown ssid>")) return Lang.tr("Подключено", "Connected");
        if (ssid.length() > 1 && ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.substring(1, ssid.length() - 1);
        return ssid;
    }
}
