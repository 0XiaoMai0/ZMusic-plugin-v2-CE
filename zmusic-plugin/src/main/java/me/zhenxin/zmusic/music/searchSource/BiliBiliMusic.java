package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.*;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.utils.NetUtils;
import me.zhenxin.zmusic.utils.OtherUtils;
import me.zhenxin.zmusic.audio.ModAudioServer;
import me.zhenxin.zmusic.language.MusicErrorMessages;

import java.net.URLEncoder;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BiliBiliMusic {

    private static final String SEARCH_API = "https://api.bilibili.com/x/web-interface/wbi/search/type";
    private static final String VIEW_API = "https://api.bilibili.com/x/web-interface/view";
    private static final String PLAY_URL_API = "https://api.bilibili.com/x/player/playurl";
    private static final String NAV_API = "https://api.bilibili.com/x/web-interface/nav";
    private static final String REFERER = "https://search.bilibili.com/";
    private static final String BILIBILI_ORIGIN = "https://www.bilibili.com";
    private static final String BILIBILI_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ZMusic/";
    private static final int[] WBI_MIXIN_KEY_ENC_TAB = {
        46, 47, 18, 2, 53, 8, 23, 32,
        15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19,
        29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63,
        57, 62, 11, 36, 20, 34, 44, 52
    };

    public static JsonObject getMusic(String keyword) {
        try {
            Gson gson = new GsonBuilder().create();
            String bvid;
            if (keyword.contains("-id:")) {
                bvid = keyword.split("-id:")[1].trim();
            } else if (keyword.matches("(?i)^BV[0-9A-Za-z]+$")) {
                bvid = keyword;
            } else {
                JsonObject video = searchFirstVideo(keyword, gson);
                if (video == null) {
                    return null;
                }
                bvid = video.get("bvid").getAsString();
            }

            JsonObject view = getVideoView(bvid, gson);
            if (view == null) {
                return null;
            }

            String musicName = cleanHighlight(view.get("title").getAsString());
            String musicSinger = view.get("owner").getAsJsonObject().get("name").getAsString();
            int musicTime = view.get("duration").getAsInt();
            long cid = view.get("cid").getAsLong();
            List<String> audios = getAudioUrls(bvid, cid, gson);
            String musicUrl = "";
            String failure = "B 站未返回独立 DASH 音频流，视频可能受登录、地区或版权限制。";
            for (String audio : audios) {
                try { musicUrl = ModAudioServer.prepare(bvid, audio); break; }
                catch (IOException error) { failure = MusicErrorMessages.requestFailure(error, "B 站音频处理失败，请稍后重试或选择其他视频。"); }
            }

            JsonObject returnJSON = new JsonObject();
            returnJSON.addProperty("id", bvid);
            returnJSON.addProperty("url", musicUrl);
            returnJSON.addProperty("time", musicTime);
            returnJSON.addProperty("name", musicName);
            returnJSON.addProperty("singer", musicSinger);
            returnJSON.addProperty("lyric", "");
            returnJSON.addProperty("lyricTr", "");
            returnJSON.addProperty("error", musicUrl.isEmpty() ? failure : "");
            return returnJSON;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public static JsonArray getMusicList(String keyword) {
        try {
            Gson gson = new GsonBuilder().create();
            JsonObject searchJson = searchVideos(keyword, 10, gson);
            if (searchJson == null || !searchJson.has("data")) {
                return null;
            }

            JsonArray searchResultList = searchJson.get("data").getAsJsonObject().get("result").getAsJsonArray();
            JsonArray returnJSON = new JsonArray();
            for (JsonElement json : searchResultList) {
                JsonObject video = json.getAsJsonObject();
                String musicName = cleanHighlight(video.get("title").getAsString());
                String musicSinger = video.get("author").getAsString();
                String musicId = video.get("bvid").getAsString();
                JsonObject returnJSONObj = new JsonObject();
                returnJSONObj.addProperty("id", musicId);
                returnJSONObj.addProperty("name", musicName);
                returnJSONObj.addProperty("singer", musicSinger);
                returnJSON.add(returnJSONObj);
            }
            return returnJSON;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static JsonObject searchFirstVideo(String keyword, Gson gson) {
        JsonObject searchJson = searchVideos(keyword, 1, gson);
        if (searchJson == null || !searchJson.has("data")) {
            return null;
        }
        JsonArray result = searchJson.get("data").getAsJsonObject().get("result").getAsJsonArray();
        if (result.size() == 0) {
            return null;
        }
        return result.get(0).getAsJsonObject();
    }

    private static JsonObject searchVideos(String keyword, int pageSize, Gson gson) {
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("keyword", keyword);
            params.put("page", "1");
            params.put("page_size", String.valueOf(pageSize));
            params.put("search_type", "video");

            String url = SEARCH_API + "?" + buildWbiQuery(params, gson);
            String searchJsonText = NetUtils.getNetStringBiliBiliWeb(url, REFERER);
            JsonObject searchJson = gson.fromJson(searchJsonText, JsonObject.class);
            if (searchJson == null || searchJson.get("code").getAsInt() != 0) {
                return null;
            }
            return searchJson;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static JsonObject getVideoView(String bvid, Gson gson) {
        try {
            String viewJsonText = NetUtils.getNetStringBiliBiliWeb(VIEW_API + "?bvid=" + URLEncoder.encode(bvid, "UTF-8"), REFERER);
            JsonObject viewJson = gson.fromJson(viewJsonText, JsonObject.class);
            if (viewJson == null || viewJson.get("code").getAsInt() != 0) {
                return null;
            }
            return viewJson.get("data").getAsJsonObject();
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static List<String> getAudioUrls(String bvid, long cid, Gson gson) {
        List<String> candidates = new ArrayList<>();
        try {
            String url = PLAY_URL_API +
                "?bvid=" + URLEncoder.encode(bvid, "UTF-8") +
                "&cid=" + cid +
                "&fnver=0&fnval=4048&qn=32&fourk=1";
            String playJsonText = NetUtils.getNetStringBiliBiliWeb(url, "https://www.bilibili.com/video/" + bvid);
            JsonObject playJson = gson.fromJson(playJsonText, JsonObject.class);
            if (playJson == null || playJson.get("code").getAsInt() != 0) {
                return candidates;
            }

            JsonObject data = playJson.get("data").getAsJsonObject();
            if (data.has("dash") && data.get("dash").getAsJsonObject().has("audio")) {
                JsonArray audios = data.get("dash").getAsJsonObject().get("audio").getAsJsonArray();
                JsonObject selected = null;
                // 优先标准 AAC-LC，避免低码率 HE-AAC 的兼容性和额外解码开销。
                for (JsonElement audio : audios) {
                    JsonObject item = audio.getAsJsonObject();
                    if (item.get("id").getAsInt() == 30232) { selected = item; break; }
                }
                for (JsonElement audio : audios) {
                    if (selected != null) break;
                    JsonObject item = audio.getAsJsonObject();
                    if (item.has("codecs") && item.get("codecs").getAsString().startsWith("mp4a.40.2")) selected = item;
                }
                if (selected == null && audios.size() > 0) selected = audios.get(0).getAsJsonObject();
                if (selected != null) {
                    candidates.add(selected.get("baseUrl").getAsString());
                    if (selected.has("backupUrl") && selected.get("backupUrl").isJsonArray()) {
                        for (JsonElement backup : selected.getAsJsonArray("backupUrl")) candidates.add(backup.getAsString());
                    }
                }
            }
            // 优先官方 HTTPS CDN，家庭节点的非标准端口常被服务器防火墙阻断。
            candidates.removeIf(candidate -> !ModAudioServer.allowedMediaUrl(candidate));
            candidates.sort(Comparator.comparingInt(candidate -> URI.create(candidate).getPort() == -1 ? 0 : 1));
            // durl 可能是视频流，不能当成独立音频返回。
            return candidates;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return candidates;
        }
    }

    private static String buildWbiQuery(Map<String, String> params, Gson gson) throws Exception {
        params.replaceAll((key, value) -> value.replaceAll("[!'()*]", ""));
        WbiKeys keys = getWbiKeys(gson);
        String mixinKey = getMixinKey(keys.imgKey + keys.subKey);
        params.put("wts", String.valueOf(System.currentTimeMillis() / 1000));

        String query = buildQuery(params, true);
        params.put("w_rid", OtherUtils.getMD5String(query + mixinKey));
        return buildQuery(params, false);
    }

    private static WbiKeys getWbiKeys(Gson gson) throws Exception {
        String navJsonText = NetUtils.getNetStringBiliBiliWeb(NAV_API, REFERER);
        JsonObject navJson = gson.fromJson(navJsonText, JsonObject.class);
        JsonObject wbiImg = navJson.get("data").getAsJsonObject().get("wbi_img").getAsJsonObject();
        return new WbiKeys(getFileName(wbiImg.get("img_url").getAsString()), getFileName(wbiImg.get("sub_url").getAsString()));
    }

    private static String getMixinKey(String key) {
        StringBuilder sb = new StringBuilder();
        for (int index : WBI_MIXIN_KEY_ENC_TAB) {
            sb.append(key.charAt(index));
        }
        return sb.substring(0, 32);
    }

    private static String buildQuery(Map<String, String> params, boolean filter) throws Exception {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            String value = filter ? entry.getValue().replaceAll("[!'()*]", "") : entry.getValue();
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append("=")
                .append(urlEncode(value));
        }
        return query.toString();
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
    }

    private static String getFileName(String url) {
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        return fileName.substring(0, fileName.indexOf("."));
    }

    private static String cleanHighlight(String text) {
        return text.replaceAll("<[^>]+>", "")
            .replaceAll("&quot;", "\"")
            .replaceAll("&#39;", "'")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">");
    }

    private static class WbiKeys {
        private final String imgKey;
        private final String subKey;

        private WbiKeys(String imgKey, String subKey) {
            this.imgKey = imgKey;
            this.subKey = subKey;
        }
    }
}
