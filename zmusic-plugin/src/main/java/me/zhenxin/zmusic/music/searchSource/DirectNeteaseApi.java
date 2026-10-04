package me.zhenxin.zmusic.music.searchSource;
import com.google.gson.*;
import me.zhenxin.zmusic.utils.CookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;
/** 网易云内置网页接口，不依赖公共中转 API；账号权益由平台验证。 */
public final class DirectNeteaseApi {
    private static final Gson GSON = new Gson();
    private static final String ROOT = "https://music.163.com";
    private DirectNeteaseApi() { }
    public static String request(String route, String body) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            int query = route.indexOf('?');
            if (query >= 0) { parse(route.substring(query + 1), params); route = route.substring(0, query); }
            parse(body, params);
            JsonObject result;
            switch (route) {
                case "search":
                    result = get("/api/search/get/web", "s", params.get("keywords"), "type", "1",
                            "limit", params.getOrDefault("limit", "10"), "offset", "0");
                    break;
                case "song/detail":
                    result = get("/api/song/detail", "ids", "[" + params.get("ids") + "]");
                    if (result.has("songs")) for (JsonElement element : result.getAsJsonArray("songs")) {
                        JsonObject song = element.getAsJsonObject();
                        if (!song.has("dt") && song.has("duration")) song.add("dt", song.get("duration"));
                        if (!song.has("ar") && song.has("artists")) song.add("ar", song.get("artists"));
                    }
                    break;
                case "lyric":
                    result = get("/api/song/lyric", "id", params.get("id"), "lv", "-1", "tv", "-1", "kv", "-1");
                    break;
                case "song/url/v1":
                    result = get("/api/song/enhance/player/url", "ids", "[" + params.get("id") + "]", "br", "320000");
                    JsonArray data = result.has("data") && result.get("data").isJsonArray() ? result.getAsJsonArray("data") : new JsonArray();
                    if (data.size() == 0 || !data.get(0).getAsJsonObject().has("url")
                            || data.get(0).getAsJsonObject().get("url").isJsonNull()) {
                        String outer = ROOT + "/song/media/outer/url?id=" + URLEncoder.encode(params.get("id"), "UTF-8") + ".mp3";
                        JsonObject item = new JsonObject();
                        item.addProperty("url", WebMusicUtils.hasContentLength(outer, ROOT + "/", CookieUtils.getCookies()) ? outer : "");
                        data = new JsonArray(); data.add(item); result.add("data", data);
                    }
                    break;
                case "playlist/detail":
                    result = get("/api/v6/playlist/detail", "id", params.get("id"), "n", "1000", "s", "0");
                    break;
                case "login/status":
                    result = new JsonObject(); result.add("data", get("/api/nuser/account/get"));
                    break;
                default:
                    result = new JsonObject();
                    result.addProperty("code", 501);
                    result.addProperty("message", "内置网易云模式支持 Cookie 登录。二维码、密码、验证码登录请在 api.netease 配置自建 NeteaseCloudMusicApi 地址。");
            }
            return result.toString();
        } catch (Exception error) {
            JsonObject result = new JsonObject(); result.addProperty("code", 502);
            result.addProperty("message", "网易云接口请求失败: " + error.getClass().getSimpleName());
            return result.toString();
        }
    }
    private static JsonObject get(String route, String... pairs) throws Exception {
        StringBuilder url = new StringBuilder(ROOT).append(route).append("?timestamp=").append(System.currentTimeMillis());
        for (int index = 0; index < pairs.length; index += 2) {
            url.append('&').append(URLEncoder.encode(pairs[index], "UTF-8")).append('=')
                    .append(URLEncoder.encode(pairs[index + 1] == null ? "" : pairs[index + 1], "UTF-8"));
        }
        return GSON.fromJson(WebMusicUtils.get(url.toString(), ROOT + "/", CookieUtils.getCookies()), JsonObject.class);
    }
    private static void parse(String content, Map<String, String> target) throws Exception {
        if (content == null || content.isEmpty()) return;
        for (String pair : content.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) target.put(URLDecoder.decode(parts[0], "UTF-8"), URLDecoder.decode(parts[1], "UTF-8"));
        }
    }
}
