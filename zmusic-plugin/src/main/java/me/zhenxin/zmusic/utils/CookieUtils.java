package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;

import java.io.File;
import java.io.IOException;

/**
 * Cookie 工具类
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/3/21 12:25
 */
public class CookieUtils {

    private static final File COOKIE_FILE = new File(ZMusic.dataFolder, "cookies.txt");
    private static String cookieString = "";

    public static void initCookieManager() {
        if (!COOKIE_FILE.exists()) {
            try {
                COOKIE_FILE.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        cookieString = normalizeCookie(OtherUtils.readFileToString(COOKIE_FILE));
    }

    public static void saveCookies(String cookie) {
        cookieString = normalizeCookie(cookie);
        try {
            OtherUtils.saveStringToLocal(COOKIE_FILE, cookieString);
        } catch (IOException e) {
            ZMusic.log.sendDebugMessage("[CookieUtils] 保存Cookies失败: " + e.getMessage());
        }
    }

    public static String getCookies() {
        cookieString = normalizeCookie(cookieString);
        return cookieString;
    }

    private static String normalizeCookie(String cookie) {
        if (cookie == null) {
            return "";
        }
        String normalized = cookie.trim();
        if (normalized.regionMatches(true, 0, "cookie:", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        normalized = normalized.replace('\r', ';').replace('\n', ';').replace('\t', ' ');
        StringBuilder safe = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 32 && c <= 126) {
                safe.append(c);
            }
        }
        normalized = safe.toString();
        while (normalized.contains(";;")) {
            normalized = normalized.replace(";;", ";");
        }
        while (normalized.startsWith(";")) {
            normalized = normalized.substring(1).trim();
        }
        while (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized.trim();
    }
}
