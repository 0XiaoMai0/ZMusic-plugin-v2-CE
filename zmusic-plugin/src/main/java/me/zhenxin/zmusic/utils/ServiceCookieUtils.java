package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class ServiceCookieUtils {

    private ServiceCookieUtils() {
    }

    public static void saveCookies(String service, String rawCookies) {
        String cookie = normalizeRawCookie(rawCookies);
        try {
            OtherUtils.saveStringToLocal(getCookieFile(service), cookie);
        } catch (IOException e) {
            ZMusic.log.sendDebugMessage("[ServiceCookieUtils] Failed to save " + service + " cookies: " + e.getMessage());
        }
    }

    public static String getCookies(String service) {
        File file = getCookieFile(service);
        if (!file.exists()) {
            return "";
        }
        return normalizeRawCookie(OtherUtils.readFileToString(file));
    }

    public static boolean hasCookies(String service) {
        return !getCookies(service).isEmpty();
    }

    public static String getCookieValue(String cookie, String key) {
        if (cookie == null || key == null) {
            return "";
        }
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(key)) {
                return kv[1].trim();
            }
        }
        return "";
    }

    public static String normalizeRawCookie(String rawCookies) {
        if (rawCookies == null) {
            return "";
        }
        String cookie = rawCookies.trim();
        if (cookie.regionMatches(true, 0, "cookie:", 0, 7)) {
            cookie = cookie.substring(7).trim();
        }
        cookie = cookie.replace('\n', ';').replace('\r', ';').replace('\t', ' ');
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < cookie.length(); i++) {
            char c = cookie.charAt(i);
            if (c >= 32 && c <= 126) {
                safe.append(c);
            }
        }
        cookie = safe.toString();
        while (cookie.contains(";;")) {
            cookie = cookie.replace(";;", ";");
        }
        while (cookie.startsWith(";")) {
            cookie = cookie.substring(1).trim();
        }
        while (cookie.endsWith(";")) {
            cookie = cookie.substring(0, cookie.length() - 1).trim();
        }
        return cookie.trim();
    }

    private static File getCookieFile(String service) {
        String safeService = service == null ? "music" : service.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return new File(ZMusic.dataFolder, safeService + "-cookies.txt");
    }
}
