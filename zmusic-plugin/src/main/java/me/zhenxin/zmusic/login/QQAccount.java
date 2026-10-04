package me.zhenxin.zmusic.login;

import com.google.gson.JsonObject;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;

/** QQ、微信、手机号登录凭据的统一鉴权字段，不保存密码。 */
public final class QQAccount {
    private QQAccount() { }
    public static String value(String cookies, String... names) {
        for (String name : names) {
            String value = ServiceCookieUtils.getCookieValue(cookies, name);
            if (!value.isEmpty()) return value;
        }
        return "";
    }
    public static String uin(String cookies) {
        String value = value(cookies, "qqmusic_uin", "musicid", "uin");
        if (value.startsWith("o")) value = value.substring(1);
        return value.matches("\\d+") ? value.replaceFirst("^0+(?!$)", "") : "0";
    }
    public static String key(String cookies) { return value(cookies, "qqmusic_key", "qm_keyst", "musickey", "music_key"); }
    public static int hash(String value, int seed) {
        int hash = seed;
        for (int index = 0; index < value.length(); index++) hash += (hash << 5) + value.charAt(index);
        return hash & 0x7fffffff;
    }
    public static int loginType(String cookies) {
        String saved = value(cookies, "tmeLoginType", "login_type");
        if (saved.matches("[012]")) return Integer.parseInt(saved);
        return key(cookies).startsWith("W_X") ? 1 : 2;
    }
    public static JsonObject comm(String cookies) {
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 4747474);
        comm.addProperty("format", "json");
        comm.addProperty("platform", "yqq.json");
        comm.addProperty("uin", uin(cookies));
        comm.addProperty("g_tk", hash(key(cookies), 5381));
        comm.addProperty("g_tk_new_20200303", hash(key(cookies), 5381));
        if (!key(cookies).isEmpty()) {
            comm.addProperty("authst", key(cookies));
            comm.addProperty("tmeLoginType", loginType(cookies));
        }
        return comm;
    }
    public static String credentialCookies(JsonObject data, int type) {
        String id = get(data, "musicid", get(data, "str_musicid", ""));
        String key = get(data, "musickey", "");
        if (!id.matches("\\d+") || id.equals("0") || key.isEmpty() || !key.matches("[\\x21-\\x7E&&[^;]]+")) {
            throw new IllegalStateException("QQ音乐未返回有效的音乐账号凭据。");
        }
        return "qqmusic_uin=" + id + "; musicid=" + id + "; qqmusic_key=" + key
                + "; qm_keyst=" + key + "; musickey=" + key + "; tmeLoginType=" + type;
    }
    public static String get(JsonObject json, String key, String fallback) {
        try { return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
