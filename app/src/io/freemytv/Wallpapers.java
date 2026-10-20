package io.freemytv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The home screen's wallpapers: landscape photos from Wikimedia Commons (featured pictures with free licences, listed
 * with their authors and licences in assets/wallpapers.json), a new one every 2 minutes. They download as needed and the last
 * CACHE stay on the TV (its storage is small); the built-in assets/wallpaper.jpg covers the time before the first one.
 */
final class Wallpapers {
    static final long EVERY_MS = 2 * 60_000L;
    private static final int CACHE = 12;

    private final List<String> photos = new ArrayList<>();   // their 1920 px URLs
    private final File dir;

    Wallpapers(Context c) {
        dir = new File(c.getFilesDir(), "wallpapers");
        try (InputStream in = c.getAssets().open("wallpapers.json")) {
            JSONArray list = new JSONArray(read(in));
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                photos.add(o.getString("url"));
            }
        } catch (Exception e) {
            AppLog.w("wallpapers: " + e.getMessage());
        }
    }

    /** The slot for `now` (changes every EVERY_MS, the same on every start), or -1 with no list. */
    int slot(long now) {
        return photos.isEmpty() ? -1 : (int) ((now / EVERY_MS) % photos.size());
    }

    int count() {
        return photos.size();
    }

    /** The slot's photo on disk, downloading it first if needed (background threads only); null if it can't be had. */
    File file(int slot) {
        if (slot < 0 || slot >= photos.size()) return null;
        String url = photos.get(slot);
        File f = new File(dir, Integer.toHexString(url.hashCode()) + ".jpg");
        if (f.length() > 0) {
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis());
            return f;
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File part = new File(f.getPath() + ".part");
        if (download(url, f, part, false) || (Vpn.configured() && download(url, f, part, true))) {
            trim();
            return f;
        }
        return null;
    }

    /** One download try, direct or (Wikimedia is sometimes unreachable from Russian networks) through the VPN. */
    private boolean download(String url, File f, File part, boolean viaVpn) {
        try {
            HttpURLConnection c = Net.open(url, viaVpn);
            c.setReadTimeout(30_000);
            // Wikimedia asks every client to name itself.
            c.setRequestProperty("User-Agent", "FreeMyTV-home/1.0 (Android TV home screen)");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            } finally {
                c.disconnect();
            }
            if (!part.renameTo(f)) throw new IOException("rename");
            return true;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
            AppLog.w("wallpaper download" + (viaVpn ? " via VPN: " : ": ") + e.getMessage());
            return false;
        }
    }

    /** Keeps only the CACHE most recently used photos. */
    private void trim() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (files == null || files.length <= CACHE) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        //noinspection ResultOfMethodCallIgnored
        for (int i = CACHE; i < files.length; i++) files[i].delete();
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
