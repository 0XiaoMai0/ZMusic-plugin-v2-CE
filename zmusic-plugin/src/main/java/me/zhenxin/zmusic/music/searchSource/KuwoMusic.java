package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.utils.KuwoWebAuth;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;




import java.net.URLEncoder;
import java.util.Locale;
import java.util.UUID;

public class KuwoMusic {

    public static JsonObject getMusicUrl(String musicName) {
        try {
            Gson gson = new GsonBuilder().create();
            JsonObject selected;
            if (musicName.contains("-id:")) {
                String id = musicName.substring(musicName.indexOf("-id:") + 4).trim();
                selected = new JsonObject();
                selected.addProperty("id", id);
                selected.addProperty("name", id);
                selected.addProperty("singer", "酷我音乐");
                selected.addProperty("time", 0);
                JsonObject cached = SongMetadataCache.get("kuwo", id);
                if (cached != null) selected = cached;
            } else {
                JsonArray list = getMusicList(musicName);
                if (list == null || list.size() == 0) {
                    return null;
                }
                selected = list.get(0).getAsJsonObject();
            }

            String musicID = getString(selected, "id", "");
            String name = clean(getString(selected, "name", musicID));
            String singer = clean(getString(selected, "singer", "酷我音乐"));
            int time = getInt(selected, "time", 0);
            String cookie = ServiceCookieUtils.getCookies("kuwo");
            String url = getKuwoWebPlayUrl(gson, musicID, cookie);
            if (url == null || url.isEmpty()) {
                url = WebMusicUtils.get("https://antiserver.kuwo.cn/anti.s?type=convert_url&format=mp3&rid=" + musicID, null, null);
            }
            if (url == null || url.trim().isEmpty() || url.contains("anti.s")) {
                return errorResult(musicID, name, singer, time,
                    "酷我暂未提供这首歌的播放地址，请选择其他版本或联系管理员检查酷我账号登录状态与歌曲权限。");
            }

            JsonObject returnJson = new JsonObject();
            returnJson.addProperty("id", musicID);
            returnJson.addProperty("url", url);
            returnJson.addProperty("name", name);
            returnJson.addProperty("time", time);
            returnJson.addProperty("singer", singer);
            String[] lyrics = getLyrics(gson, musicID);
            // 直接输入 ID 且没有搜索缓存时，从平台歌词头补齐歌名和歌手。
            if (name.equals(musicID)) returnJson.addProperty("name", lyricMetadata(lyrics[0], "ti", name));
            if (singer.equals("酷我音乐")) returnJson.addProperty("singer", lyricMetadata(lyrics[0], "ar", singer));
            returnJson.addProperty("lyric", lyrics[0]);
            returnJson.addProperty("lyricTr", lyrics[1]);
            returnJson.addProperty("error", "");
            long lyricEnd = me.zhenxin.zmusic.music.LyricParser.parseSeconds(lyrics[0]).keySet().stream().mapToLong(Long::longValue).max().orElse(0);
            long expected = Math.max(time, lyricEnd);
            double actual = WebMusicUtils.probeMp3Seconds(url);
            if (expected > 60 && actual > 0 && actual < expected * 0.5) {
                returnJson.addProperty("url", "");
                returnJson.addProperty("error", "酷我仅返回约 " + (int) actual + " 秒的片段或版权提示音，与歌曲时长不符；请登录有权限的账号或选择其他曲目。");
            }
            return returnJson;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public static JsonArray getMusicList(String musicName) {
        try {
            String getUrl = "https://search.kuwo.cn/r.s?all=" + URLEncoder.encode(musicName, "utf-8")
                + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8";
            String jsonStr = WebMusicUtils.get(getUrl, "https://www.kuwo.cn/", null);
            jsonStr = jsonStr.replaceAll("&nbsp;", " ");
            Gson gson = new GsonBuilder().create();
            JsonObject json = gson.fromJson(jsonStr, JsonObject.class);
            JsonArray abslist = json.getAsJsonArray("abslist");
            JsonArray returnJson = new JsonArray();
            for (JsonElement j : abslist) {
                JsonObject song = j.getAsJsonObject();
                String musicID = getString(song, "MUSICRID", "").replaceAll("MUSIC_", "");
                if (musicID.isEmpty()) {
                    continue;
                }
                JsonObject returnJsonObj = new JsonObject();
                returnJsonObj.addProperty("id", musicID);
                returnJsonObj.addProperty("name", clean(getString(song, "NAME", musicID)));
                returnJsonObj.addProperty("singer", clean(getString(song, "ARTIST", "")));
                returnJsonObj.addProperty("time", getInt(song, "DURATION", 0));
                returnJson.add(returnJsonObj);
            }
            SongMetadataCache.remember("kuwo", returnJson);
            return returnJson;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static String getKuwoWebPlayUrl(Gson gson, String musicID, String cookie) {
        try {
            String url = "https://www.kuwo.cn/api/v1/www/music/playUrl?mid="
                + URLEncoder.encode(musicID, "UTF-8")
                + "&type=music&httpsStatus=1&plat=web_www&from=&reqId=" + UUID.randomUUID().toString();
            JsonObject json = gson.fromJson(getKuwoWeb(url, "https://www.kuwo.cn/play_detail/" + musicID, cookie), JsonObject.class);
            return getString(getObject(json, "data"), "url", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String[] getLyrics(Gson gson, String musicID) {
        try {
            JsonObject data = null;
            for (String host : new String[]{"m.kuwo.cn", "www.kuwo.cn"}) {
                try {
                    String url = "https://" + host + "/newh5/singles/songinfoandlrc?musicId="
                            + URLEncoder.encode(musicID, "UTF-8");
                    JsonObject json = gson.fromJson(WebMusicUtils.get(url, "https://m.kuwo.cn/yinyue/" + musicID,
                            ServiceCookieUtils.getCookies("kuwo")), JsonObject.class);
                    data = getObject(json, "data");
                    if (data != null && data.has("lrclist") && data.get("lrclist").isJsonArray()) break;
                } catch (Exception ignored) { }
            }
            if (data == null) return new String[] {KuwoLyrics.fetch(musicID), ""};
            String lyric = formatKuwoLyricArray(data, "lrclist");
            if (lyric.isEmpty()) lyric = KuwoLyrics.fetch(musicID);
            JsonObject info = getObject(data, "songinfo");
            if (info != null) lyric = "[ti:" + getString(info, "songName", musicID) + "]\n[ar:"
                    + getString(info, "artist", "酷我音乐") + "]\n" + lyric;
            String lyricTr = firstNonEmpty(data,
                "trans", "translation", "translate", "translated_lyrics", "translate_lyrics",
                "lyrics_trans", "lyrics_translate", "trans_lyrics", "tlyrics", "tlyric");
            if (lyricTr.isEmpty()) {
                lyricTr = formatFirstKuwoLyricArray(data, "translist", "transList", "translateList",
                    "translationList", "tlyriclist", "tlyricList");
            }
            return new String[] {lyric.replace("\r", ""), lyricTr.replace("\r", "")};
        } catch (Exception ignored) {
            return new String[] {KuwoLyrics.fetch(musicID), ""};
        }
    }

    private static String formatKuwoLyricArray(JsonObject data, String key) {
        if (data == null || !data.has(key) || !data.get(key).isJsonArray()) {
            return "";
        }
        JsonArray array = data.getAsJsonArray(key);
        if (array.size() == 0) {
            return "";
        }
        StringBuilder lyric = new StringBuilder();
        for (JsonElement element : array) {
            JsonObject line = element.getAsJsonObject();
            lyric.append(formatTime(getString(line, "time", "0")))
                .append(getString(line, "lineLyric", getString(line, "lyric", "")))
                .append("\n");
        }
        return lyric.toString();
    }

    private static String lyricMetadata(String lyric, String key, String fallback) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?mi)^\\[" + key + ":([^\\]]+)\\]").matcher(lyric);
        return matcher.find() ? matcher.group(1).trim() : fallback;
    }

    private static String formatFirstKuwoLyricArray(JsonObject data, String... keys) {
        for (String key : keys) {
            String lyric = formatKuwoLyricArray(data, key);
            if (!lyric.isEmpty()) {
                return lyric;
            }
        }
        return "";
    }

    private static String firstNonEmpty(JsonObject obj, String... keys) {
        for (String key : keys) {
            String value = getString(obj, key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String getKuwoWeb(String url, String referer, String cookie) throws Exception {
        return KuwoWebAuth.get(url, referer, cookie);
    }

    private static JsonObject errorResult(String id, String name, String singer, int time, String error) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("url", "");
        result.addProperty("time", time);
        result.addProperty("name", name);
        result.addProperty("singer", singer);
        result.addProperty("lyric", "");
        result.addProperty("lyricTr", "");
        result.addProperty("error", error);
        return result;
    }

    private static String formatTime(String secondsText) {
        try {
            double seconds = Double.parseDouble(secondsText);
            long total = Math.max(0, Math.round(seconds * 100));
            long min = total / 6000;
            long sec = total / 100 % 60;
            long cent = total % 100;
            return String.format(Locale.ROOT, "[%02d:%02d.%02d]", min, sec, cent);
        } catch (Exception ignored) {
            return "[00:00.00]";
        }
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value
            .replaceAll("<[^>]+>", "")
            .replace("&nbsp;", " ")
            .replace("\\\\u0026", "&")
            .trim();
    }
}
