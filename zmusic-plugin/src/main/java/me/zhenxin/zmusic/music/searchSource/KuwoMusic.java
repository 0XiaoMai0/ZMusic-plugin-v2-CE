package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.utils.NetUtils;
import me.zhenxin.zmusic.utils.OtherUtils;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
                selected.addProperty("singer", "Kuwo Music");
                selected.addProperty("time", 0);
            } else {
                JsonArray list = getMusicList(musicName);
                if (list == null || list.size() == 0) {
                    return null;
                }
                selected = list.get(0).getAsJsonObject();
            }

            String musicID = getString(selected, "id", "");
            String name = clean(getString(selected, "name", musicID));
            String singer = clean(getString(selected, "singer", "Kuwo Music"));
            int time = getInt(selected, "time", 0);
            String cookie = ServiceCookieUtils.getCookies("kuwo");
            String url = getKuwoWebPlayUrl(gson, musicID, cookie);
            if (url == null || url.isEmpty()) {
                url = NetUtils.getNetString("http://antiserver.kuwo.cn/anti.s?type=convert_url&format=mp3&rid=" + musicID, null);
            }
            if (url == null || url.trim().isEmpty() || url.contains("anti.s")) {
                return errorResult(musicID, name, singer, time,
                    "Kuwo official API did not return a playable URL. Try /zm login kuwo raw <full Network Cookie>.");
            }

            JsonObject returnJson = new JsonObject();
            returnJson.addProperty("id", musicID);
            returnJson.addProperty("url", url);
            returnJson.addProperty("name", name);
            returnJson.addProperty("time", time);
            returnJson.addProperty("singer", singer);
            String[] lyrics = getLyrics(gson, musicID);
            returnJson.addProperty("lyric", lyrics[0]);
            returnJson.addProperty("lyricTr", lyrics[1]);
            returnJson.addProperty("error", "");
            return returnJson;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JsonArray getMusicList(String musicName) {
        try {
            String getUrl = "http://search.kuwo.cn/r.s?all=" + URLEncoder.encode(musicName, "utf-8")
                + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8";
            String jsonStr = NetUtils.getNetString(getUrl, null);
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
            return returnJson;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getKuwoWebPlayUrl(Gson gson, String musicID, String cookie) {
        try {
            String url = "https://www.kuwo.cn/api/v1/www/music/playUrl?mid="
                + URLEncoder.encode(musicID, "UTF-8")
                + "&type=music&httpsStatus=1&reqId=" + UUID.randomUUID().toString();
            JsonObject json = gson.fromJson(getKuwoWeb(url, "https://www.kuwo.cn/play_detail/" + musicID, cookie), JsonObject.class);
            return getString(getObject(json, "data"), "url", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String[] getLyrics(Gson gson, String musicID) {
        try {
            String url = "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId="
                + URLEncoder.encode(musicID, "UTF-8");
            String text = WebMusicUtils.get(url, "https://m.kuwo.cn/", ServiceCookieUtils.getCookies("kuwo"));
            JsonObject json = gson.fromJson(text, JsonObject.class);
            JsonObject data = getObject(json, "data");
            if (data == null) {
                return new String[] {"", ""};
            }
            String lyric = formatKuwoLyricArray(data, "lrclist");
            String lyricTr = firstNonEmpty(data,
                "trans", "translation", "translate", "translated_lyrics", "translate_lyrics",
                "lyrics_trans", "lyrics_translate", "trans_lyrics", "tlyrics", "tlyric");
            if (lyricTr.isEmpty()) {
                lyricTr = formatFirstKuwoLyricArray(data, "translist", "transList", "translateList",
                    "translationList", "tlyriclist", "tlyricList");
            }
            return new String[] {lyric.replace("\r", ""), lyricTr.replace("\r", "")};
        } catch (Exception ignored) {
            return new String[] {"", ""};
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
        URL getUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
        con.setReadTimeout(20000);
        con.setConnectTimeout(5000);
        con.addRequestProperty("Charset", "UTF-8");
        con.addRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        con.addRequestProperty("Referer", referer);
        if (cookie != null && !cookie.isEmpty()) {
            con.addRequestProperty("Cookie", cookie);
            String token = ServiceCookieUtils.getCookieValue(cookie, "kw_token");
            if (!token.isEmpty()) {
                con.addRequestProperty("csrf", token);
            }
        }
        int code = con.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        if (input == null) {
            return "";
        }
        String text = OtherUtils.readInputStream(input);
        input.close();
        return text;
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
            int min = (int) (seconds / 60);
            double secValue = seconds - min * 60;
            int sec = (int) secValue;
            int cent = (int) Math.round((secValue - sec) * 100);
            if (cent >= 100) {
                sec++;
                cent -= 100;
            }
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
