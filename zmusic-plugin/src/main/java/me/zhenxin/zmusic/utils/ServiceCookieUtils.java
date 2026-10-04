package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;

public class ServiceCookieUtils {

    private ServiceCookieUtils() {
    }

    /** 创建空凭据文件供管理员填写，不覆盖已经保存的账号。 */
    public static void initializeFiles() {
        try {
            Files.createDirectories(ZMusic.dataFolder.toPath());
            for (String service : new String[]{"qq", "kugou", "kuwo", "bilibili"}) {
                try { Files.createFile(getCookieFile(service).toPath()); }
                catch (FileAlreadyExistsException ignored) {
                    if (!Files.isRegularFile(getCookieFile(service).toPath())) throw new IOException("凭据文件路径不是普通文件。");
                }
            }
        } catch (IOException error) { throw new IllegalStateException("无法创建音乐平台凭据文件，请检查配置目录权限。", error); }
    }

    public static synchronized void saveCookies(String service, String rawCookies) {
        String cookie = normalizeRawCookie(rawCookies);
        Path pending = null;
        try {
            Path destination = getCookieFile(service).toPath();
            Files.createDirectories(destination.getParent());
            pending = Files.createTempFile(destination.getParent(), ".zmusic-login-", ".tmp");
            Files.write(pending, cookie.getBytes(StandardCharsets.UTF_8));
            try { Files.move(pending, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(pending, destination, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            throw new IllegalStateException("无法保存平台登录凭据，请检查配置目录权限。", e);
        } finally {
            if (pending != null) try { Files.deleteIfExists(pending); } catch (IOException ignored) { }
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
