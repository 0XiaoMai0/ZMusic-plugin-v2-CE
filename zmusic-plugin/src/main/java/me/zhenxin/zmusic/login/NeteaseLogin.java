package me.zhenxin.zmusic.login;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.utils.CookieUtils;
import me.zhenxin.zmusic.utils.NetUtils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

public class NeteaseLogin {

    private static final Gson GSON = new Gson();

    private static String api() {
        String root = Config.neteaseApiRoot;
        if (root == null || root.isEmpty()) {
            throw new IllegalStateException("Netease API root is not loaded.");
        }
        return root.endsWith("/") ? root : root + "/";
    }

    public static String key() {
        JsonObject json = parseJsonObject(NetUtils.postNetString(api() + "login/qr/key", null, "noCookie=true"));
        JsonObject data = getObject(json, "data");
        String key = getString(data, "unikey", "");
        if (key.isEmpty()) {
            throw new IllegalStateException("获取 QR key 失败，请检查网易云 API 是否可用。");
        }
        return key;
    }

    public static String create(String key) throws UnsupportedEncodingException {
        String params = "key=" + URLEncoder.encode(key, "UTF-8") + "&qrimg=true&noCookie=true";
        JsonObject json = parseJsonObject(NetUtils.postNetString(api() + "login/qr/create", null, params));
        JsonObject data = getObject(json, "data");
        String url = getString(data, "qrurl", "");
        if (url.isEmpty()) {
            throw new IllegalStateException("创建二维码失败，请检查网易云 API 是否可用。");
        }
        String savedQr = saveQrImage(data);
        String savedHtml = saveQrHtml(data, url);
        String qrImageUrl = "https://api.qrserver.com/v1/create-qr-code/?size=260x260&data="
                + URLEncoder.encode(url, "UTF-8");
        StringBuilder builder = new StringBuilder();
        if (!savedHtml.isEmpty()) {
            builder.append("QR HTML file: ").append(savedHtml).append(" | ");
        }
        if (!savedQr.isEmpty()) {
            builder.append("QR PNG file: ").append(savedQr).append(" | ");
        }
        builder.append("QR web image: ").append(qrImageUrl).append(" | ");
        builder.append("Netease raw QR payload: ").append(url);
        return builder.toString();
    }

    private static String saveQrImage(JsonObject data) {
        try {
            String qrimg = getString(data, "qrimg", "");
            if (qrimg.isEmpty()) {
                return "";
            }
            int split = qrimg.indexOf(',');
            String base64 = split >= 0 ? qrimg.substring(split + 1) : qrimg;
            byte[] image = Base64.getDecoder().decode(base64);
            File file = new File(ZMusic.dataFolder, "netease-login-qr.png");
            Files.write(file.toPath(), image);
            return file.getAbsolutePath();
        } catch (Exception e) {
            ZMusic.log.sendDebugMessage("[NeteaseLogin] Failed to save QR image: " + e.getMessage());
            return "";
        }
    }

    private static String saveQrHtml(JsonObject data, String qrUrl) {
        try {
            String qrimg = getString(data, "qrimg", "");
            if (qrimg.isEmpty()) {
                return "";
            }
            File file = new File(ZMusic.dataFolder, "netease-login-qr.html");
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>ZMusic NetEase Login</title>"
                    + "<style>body{font-family:sans-serif;margin:32px;line-height:1.5}img{width:260px;height:260px}</style>"
                    + "</head><body><h1>ZMusic NetEase QR Login</h1>"
                    + "<p>Use NetEase Cloud Music app to scan this QR code.</p>"
                    + "<img alt=\"NetEase QR\" src=\"" + escapeHtml(qrimg) + "\">"
                    + "<p>Raw payload:</p><pre>" + escapeHtml(qrUrl) + "</pre>"
                    + "</body></html>";
            Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
            return file.getAbsolutePath();
        } catch (Exception e) {
            ZMusic.log.sendDebugMessage("[NeteaseLogin] Failed to save QR html: " + e.getMessage());
            return "";
        }
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static Integer check(String key) {
        String params = "key=" + key + "&noCookie=true";
        JsonObject json = parseJsonObject(NetUtils.postNetString(api() + "login/qr/check", null, params));
        int code = getInt(json, "code", -1);
        if (code == 803) {
            String cookie = getString(json, "cookie", "");
            if (cookie.isEmpty()) {
                cookie = NetUtils.getLastResponseCookie();
            }
            if (!cookie.isEmpty()) {
                CookieUtils.saveCookies(cookie);
            }
        }
        return code;
    }

    public static void welcome() {
        String cookie = CookieUtils.getCookies();
        if (cookie != null && !cookie.isEmpty() && !hasCookie(cookie, "MUSIC_U")) {
            ZMusic.log.sendErrorMessage("网易云 Cookies 缺少 MUSIC_U，当前不会被识别为已登录。");
            ZMusic.log.sendErrorMessage("请从浏览器 Network 请求头复制完整 Cookie，或使用 /zm login qr。");
            return;
        }
        String nickname = nickname();
        if (!nickname.isEmpty()) {
            ZMusic.log.sendNormalMessage("已登录网易云音乐，昵称: " + nickname);
        } else {
            ZMusic.log.sendErrorMessage("未登录网易云音乐，公开歌曲仍可尝试播放。");
            ZMusic.log.sendErrorMessage("可使用 /zm login qr、/zm login raw 或验证码登录。");
        }
    }

    public static boolean login_fromlink(String url) {
        String result = NetUtils.postNetString(url, null, "");
        JsonObject json = parseJsonObject(result);
        int codeResult = getInt(json, "code", -1);
        if (codeResult == 200) {
            String cookie = getString(json, "cookie", "");
            if (cookie.isEmpty()) {
                cookie = NetUtils.getLastResponseCookie();
            }
            if (!cookie.isEmpty()) {
                CookieUtils.saveCookies(cookie);
                welcome();
                return true;
            }
            ZMusic.log.sendErrorMessage("登录成功但没有拿到 Cookies。");
            return false;
        }
        if (result != null && result.contains("安全风险")) {
            ZMusic.log.sendErrorMessage("网易云提示安全风险，请改用二维码登录或 raw cookie 登录。");
            return false;
        }
        ZMusic.log.sendErrorMessage("网易云登录失败，code=" + codeResult + "，message="
                + getString(json, "message", getString(json, "msg", "未知错误")));
        return false;
    }

    public static boolean sendCode(String phone, String countrycode) {
        String url = api() + "captcha/sent?phone=" + phone + "&ctcode=" + countrycode;
        JsonObject json = parseJsonObject(NetUtils.postNetString(url, null, ""));
        int code = getInt(json, "code", -1);
        if (code == 200) {
            ZMusic.log.sendNormalMessage("验证码发送成功。");
            return true;
        }
        ZMusic.log.sendErrorMessage("验证码发送失败: " + getString(json, "message",
                getString(json, "msg", "接口无响应或网络超时")));
        return false;
    }

    public static boolean verify(String phone, String code, String countrycode) {
        String urlVerify = api() + "captcha/verify?phone=" + phone + "&ctcode=" + countrycode
                + "&captcha=" + code;
        JsonObject jsonVerify = parseJsonObject(NetUtils.postNetString(urlVerify, null, ""));
        int codeVerify = getInt(jsonVerify, "code", -1);
        if (codeVerify != 200) {
            ZMusic.log.sendErrorMessage("验证码校验失败: " + getString(jsonVerify, "message",
                    getString(jsonVerify, "data", "接口无响应或网络超时")));
            return false;
        }
        String url = api() + "login/cellphone?phone=" + phone + "&ctcode=" + countrycode + "&captcha=" + code;
        return login_fromlink(url);
    }

    public static boolean password_phone(String phone, String password, String countrycode, boolean isMD5) {
        String url = api() + "login/cellphone?phone=" + phone + "&ctcode=" + countrycode
                + (isMD5 ? "&md5_password=" : "&password=") + password;
        return login_fromlink(url);
    }

    public static boolean password_email(String email, String password, boolean isMD5) {
        String url = api() + "login?email=" + email
                + (isMD5 ? "&md5_password=" : "&password=") + password;
        return login_fromlink(url);
    }

    public static String nickname() {
        try {
            JsonObject data = status();
            JsonObject profile = getObject(data, "profile");
            String nickname = getString(profile, "nickname", "");
            if (!nickname.isEmpty()) {
                return nickname;
            }
            JsonObject accountInStatus = getObject(data, "account");
            nickname = getString(accountInStatus, "userName", "");
            if (!nickname.isEmpty()) {
                return nickname;
            }
            JsonObject account = account();
            return getString(getObject(account, "profile"), "nickname", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public static JsonObject status() {
        JsonObject root = parseJsonObject(NetUtils.postNetString(api() + "login/status", null, ""));
        return getObject(root, "data");
    }

    private static JsonObject account() {
        return parseJsonObject(NetUtils.postNetString(api() + "user/account", null, ""));
    }

    public static String statusSummary() {
        String cookie = CookieUtils.getCookies();
        boolean hasMusicU = hasCookie(cookie, "MUSIC_U");
        JsonObject data = status();
        int code = getInt(data, "code", -1);
        String nickname = getString(getObject(data, "profile"), "nickname", "");
        if (nickname.isEmpty()) {
            JsonObject account = account();
            nickname = getString(getObject(account, "profile"), "nickname", "");
        }
        return "MUSIC_U=" + (hasMusicU ? "present" : "missing")
                + ", login/status code=" + code
                + ", nickname=" + (nickname.isEmpty() ? "empty" : nickname);
    }

    private static JsonObject parseJsonObject(String result) {
        if (result == null || result.isEmpty()) {
            ZMusic.log.sendErrorMessage("网易云音乐 API 无响应，请检查 config.json 的 api.netease 或服务器网络。");
            return new JsonObject();
        }
        try {
            JsonElement element = GSON.fromJson(result, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                ZMusic.log.sendErrorMessage("网易云音乐 API 返回内容不是 JSON: " + result);
                return new JsonObject();
            }
            return element.getAsJsonObject();
        } catch (Exception e) {
            ZMusic.log.sendErrorMessage("网易云音乐 API 返回解析失败。");
            ZMusic.log.sendDebugMessage("[NeteaseLogin] parseJsonObject failed: " + e.getMessage());
            return new JsonObject();
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || parent.get(key) == null
                || parent.get(key).isJsonNull() || !parent.get(key).isJsonObject()) {
            return new JsonObject();
        }
        return parent.getAsJsonObject(key);
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key) == null || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key) == null || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static void loginRaw(String rawCookies) {
        if (rawCookies == null || rawCookies.isEmpty()) {
            ZMusic.log.sendErrorMessage("Cookies 不能为空。");
            return;
        }
        if (!rawCookies.contains("=")) {
            ZMusic.log.sendErrorMessage("无效的 Cookies 格式。");
            return;
        }
        String normalized = normalizeRawCookie(rawCookies);
        if (normalized.isEmpty() || !normalized.contains("=")) {
            ZMusic.log.sendErrorMessage("无效的 Cookies 格式。");
            return;
        }
        try {
            CookieUtils.saveCookies(normalized);
            ZMusic.log.sendNormalMessage("Cookies 已成功保存。");
            if (!hasCookie(normalized, "MUSIC_U")) {
                ZMusic.log.sendErrorMessage("当前 Cookies 缺少 MUSIC_U，网易云不会识别为已登录。");
                ZMusic.log.sendErrorMessage("请从浏览器 Network 请求头里的 Cookie 复制完整内容，而不是只用 document.cookie。");
                return;
            }
            welcome();
        } catch (Exception e) {
            ZMusic.log.sendErrorMessage("保存 Cookies 时发生错误: " + e.getMessage());
        }
    }

    private static String normalizeRawCookie(String rawCookies) {
        String cookie = rawCookies.trim();
        if (cookie.regionMatches(true, 0, "cookie:", 0, 7)) {
            cookie = cookie.substring(7).trim();
        }
        cookie = cookie.replace('\n', ';').replace('\r', ';');
        while (cookie.contains(";;")) {
            cookie = cookie.replace(";;", ";");
        }
        return cookie.trim();
    }

    private static boolean hasCookie(String cookie, String key) {
        String[] parts = cookie.split(";");
        for (String part : parts) {
            String item = part.trim();
            if (item.regionMatches(true, 0, key + "=", 0, key.length() + 1)) {
                return item.length() > key.length() + 1;
            }
        }
        return false;
    }
}
