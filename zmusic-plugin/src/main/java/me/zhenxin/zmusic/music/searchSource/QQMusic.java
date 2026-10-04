package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import me.zhenxin.zmusic.language.MusicErrorMessages;
import me.zhenxin.zmusic.login.QQAccount;

import java.net.URLEncoder;
import java.util.Random;

public class QQMusic {

    private static final String SEARCH_API = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String SINGLE_SONG_API = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg";
    private static final String LYRIC_API = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String MUSICU_API = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String REFERER = "https://y.qq.com/";
    private static final Gson GSON = new GsonBuilder().create();

    public static JsonObject getMusicUrl(String musicName) {
        try {
            JsonObject selected;
            if (musicName.contains("-id:")) {
                selected = SongMetadataCache.get("qq", musicName.substring(musicName.indexOf("-id:") + 4).trim());
                if (selected == null) selected = getSongDetail(parseSongMid(musicName.substring(musicName.indexOf("-id:") + 4).trim()));
                if (selected == null) {
                    selected = new JsonObject();
                    String id = musicName.substring(musicName.indexOf("-id:") + 4).trim();
                    selected.addProperty("id", id);
                    selected.addProperty("songMid", parseSongMid(id));
                    selected.addProperty("mediaMid", parseMediaMid(id));
                    selected.addProperty("name", parseSongMid(id));
                    selected.addProperty("singer", "QQ音乐");
                    selected.addProperty("time", 0);
                }
                String mediaMid = parseMediaMid(musicName.substring(musicName.indexOf("-id:") + 4).trim());
                if (!mediaMid.isEmpty()) {
                    selected.addProperty("mediaMid", mediaMid);
                    selected.addProperty("id", selected.get("songMid").getAsString() + "," + mediaMid);
                }
            } else {
                JsonArray list = getMusicList(musicName);
                if (list == null || list.size() == 0) {
                    return null;
                }
                selected = list.get(0).getAsJsonObject();
            }

            String songMid = getString(selected, "songMid", parseSongMid(getString(selected, "id", "")));
            String mediaMid = getString(selected, "mediaMid", parseMediaMid(getString(selected, "id", "")));
            if (songMid.isEmpty()) {
                return null;
            }

            String musicUrl = getPlayUrl(songMid, mediaMid);
            if (musicUrl == null || musicUrl.isEmpty()) {
                String fallback = getFallbackUrl(songMid);
                if (fallback != null && !fallback.isEmpty() && WebMusicUtils.hasContentLength(fallback, null, null)) musicUrl = fallback;
            }
            if (musicUrl == null || musicUrl.isEmpty()) {
                return errorResult(songMid + (mediaMid.isEmpty() ? "" : "," + mediaMid), selected,
                        unavailableMessage(selected, ServiceCookieUtils.getCookies("qq")));
            }
            if (!WebMusicUtils.hasContentLength(musicUrl, null, null)) {
                return errorResult(songMid + (mediaMid.isEmpty() ? "" : "," + mediaMid), selected,
                        "QQ音乐播放失败：" + MusicErrorMessages.audioStatus(WebMusicUtils.probeAudio(musicUrl, null, null)) + "。");
            }

            JsonObject result = new JsonObject();
            result.addProperty("id", songMid + (mediaMid.isEmpty() ? "" : "," + mediaMid));
            result.addProperty("url", musicUrl);
            result.addProperty("time", getInt(selected, "time", 0));
            result.addProperty("name", getString(selected, "name", songMid));
            result.addProperty("singer", getString(selected, "singer", "QQ音乐"));
            result.addProperty("lyric", getLyric(songMid, "lyric"));
            result.addProperty("lyricTr", getLyric(songMid, "trans"));
            result.addProperty("error", "");
            double actual = WebMusicUtils.probeMp3Seconds(musicUrl);
            if (getInt(selected, "time", 0) > 60 && actual > 0 && actual < getInt(selected, "time", 0) * 0.5) {
                return errorResult(result.get("id").getAsString(), selected,
                        "QQ音乐仅提供了约 " + (int) actual + " 秒的试听片段，请登录拥有这首歌播放权限的账号。");
            }
            return result;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public static JsonArray getMusicList(String musicName) {
        try {
            String cookie = ServiceCookieUtils.getCookies("qq");
            String url = SEARCH_API + "?format=json&p=1&n=10&w=" + URLEncoder.encode(musicName, "UTF-8");
            JsonObject json = GSON.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
            JsonObject data = getObject(json, "data");
            JsonObject song = getObject(data, "song");
            JsonArray list = song == null ? null : song.getAsJsonArray("list");
            if (list == null) {
                return null;
            }

            JsonArray result = new JsonArray();
            for (JsonElement element : list) {
                JsonObject item = element.getAsJsonObject();
                String songMid = getString(item, "songmid", getString(item, "mid", ""));
                String mediaMid = getString(item, "media_mid", getString(item, "strMediaMid", ""));
                if (mediaMid.isEmpty()) {
                    mediaMid = getString(getObject(item, "file"), "media_mid", "");
                }
                if (songMid.isEmpty()) {
                    continue;
                }
                JsonObject out = new JsonObject();
                out.addProperty("id", songMid + (mediaMid.isEmpty() ? "" : "," + mediaMid));
                out.addProperty("songMid", songMid);
                out.addProperty("mediaMid", mediaMid);
                out.addProperty("name", clean(getString(item, "songname", getString(item, "title", songMid))));
                out.addProperty("singer", singers(item.getAsJsonArray("singer")));
                out.addProperty("time", getInt(item, "interval", 0));
                JsonObject pay = getObject(item, "pay");
                out.addProperty("paid", getInt(pay, "payplay", getInt(pay, "pay_play", 0)) == 1);
                result.add(out);
            }
            SongMetadataCache.remember("qq", result);
            return result;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static JsonObject getSongDetail(String songMid) {
        if (songMid == null || songMid.isEmpty()) {
            return null;
        }
        try {
            String cookie = ServiceCookieUtils.getCookies("qq");
            String url = SINGLE_SONG_API + "?format=json&tpl=yqq_song_detail&songmid="
                    + URLEncoder.encode(songMid, "UTF-8");
            JsonObject json = GSON.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
            JsonArray data = json == null ? null : json.getAsJsonArray("data");
            if (data == null || data.size() == 0) {
                return null;
            }
            JsonObject song = data.get(0).getAsJsonObject();
            JsonObject file = getObject(song, "file");
            String mediaMid = getString(file, "media_mid", "");
            JsonObject out = new JsonObject();
            out.addProperty("id", songMid + (mediaMid.isEmpty() ? "" : "," + mediaMid));
            out.addProperty("songMid", songMid);
            out.addProperty("mediaMid", mediaMid);
            out.addProperty("name", clean(getString(song, "name", getString(song, "title", songMid))));
            out.addProperty("singer", singers(song.getAsJsonArray("singer")));
            out.addProperty("time", getInt(song, "interval", 0));
            JsonObject pay = getObject(song, "pay");
            out.addProperty("paid", getInt(pay, "payplay", getInt(pay, "pay_play", 0)) == 1);
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getFallbackUrl(String songMid) {
        try {
            String cookie = ServiceCookieUtils.getCookies("qq");
            String url = SINGLE_SONG_API + "?format=json&tpl=yqq_song_detail&songmid="
                    + URLEncoder.encode(songMid, "UTF-8");
            JsonObject json = GSON.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
            JsonObject urls = getObject(json, "url");
            if (urls == null || urls.entrySet().isEmpty()) {
                return "";
            }
            String value = urls.entrySet().iterator().next().getValue().getAsString();
            if (value == null || value.isEmpty()) {
                return "";
            }
            if (value.startsWith("http://") || value.startsWith("https://")) {
                return value;
            }
            return "https://" + value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JsonObject errorResult(String id, JsonObject selected, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("url", "");
        result.addProperty("time", getInt(selected, "time", 0));
        result.addProperty("name", getString(selected, "name", parseSongMid(id)));
        result.addProperty("singer", getString(selected, "singer", "QQ音乐"));
        result.addProperty("lyric", "");
        result.addProperty("lyricTr", "");
        result.addProperty("error", message);
        return result;
    }

    private static String getPlayUrl(String songMid, String mediaMid) throws Exception {
        String resolvedMediaMid = mediaMid;
        if (resolvedMediaMid == null || resolvedMediaMid.isEmpty()) {
            JsonObject detail = getSongDetail(songMid);
            resolvedMediaMid = detail == null ? "" : getString(detail, "mediaMid", "");
        }
        if (resolvedMediaMid == null || resolvedMediaMid.isEmpty()) {
            resolvedMediaMid = songMid;
        }

        String[] filenames = new String[] {
            "M800" + resolvedMediaMid + ".mp3",
            "M500" + resolvedMediaMid + ".mp3"
        };
        for (String filename : filenames) {
            String url = getPlayUrlByFilename(songMid, filename);
            if (url != null && !url.isEmpty()) {
                if (WebMusicUtils.hasContentLength(url, null, null)) return url;
            }
        }
        return "";
    }

    private static String getPlayUrlByFilename(String songMid, String filename) throws Exception {
        String cookie = ServiceCookieUtils.getCookies("qq");
        String uin = QQAccount.uin(cookie);
        String guid = guid();

        JsonObject payload = new JsonObject();
        JsonObject comm = QQAccount.comm(cookie);
        payload.add("comm", comm);

        JsonObject req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        JsonObject param = new JsonObject();
        param.addProperty("guid", guid);
        param.addProperty("uin", uin);
        param.addProperty("loginflag", QQAccount.key(cookie).isEmpty() ? 0 : 1);
        param.addProperty("platform", "20");
        JsonArray mids = new JsonArray();
        mids.add(songMid);
        param.add("songmid", mids);
        JsonArray types = new JsonArray();
        types.add(0);
        param.add("songtype", types);
        JsonArray files = new JsonArray();
        files.add(filename);
        param.add("filename", files);
        req.add("param", param);
        payload.add("req_0", req);

        JsonObject json = GSON.fromJson(WebMusicUtils.postJson(MUSICU_API, REFERER, cookie, payload.toString()), JsonObject.class);
        JsonObject req0 = getObject(json, "req_0");
        JsonObject data = getObject(req0, "data");
        if (data == null || !data.has("midurlinfo")) {
            return "";
        }
        JsonArray infos = data.getAsJsonArray("midurlinfo");
        if (infos.size() == 0) {
            return "";
        }
        JsonObject info = infos.get(0).getAsJsonObject();
        String purl = getString(info, "purl", "");
        if (purl.isEmpty()) {
            return "";
        }
        if (purl.startsWith("http://") || purl.startsWith("https://")) {
            return purl;
        }
        String sip = "https://dl.stream.qqmusic.qq.com/";
        if (data.has("sip") && data.get("sip").isJsonArray() && data.getAsJsonArray("sip").size() > 0) {
            sip = data.getAsJsonArray("sip").get(0).getAsString();
            if (sip.startsWith("http://")) {
                sip = "https://" + sip.substring("http://".length());
            }
        }
        return sip + purl;
    }

    private static String getLyric(String songMid, String field) {
        if (songMid == null || songMid.isEmpty()) {
            return "";
        }
        try {
            String cookie = ServiceCookieUtils.getCookies("qq");
            String url = LYRIC_API + "?format=json&nobase64=1&songmid="
                    + URLEncoder.encode(songMid, "UTF-8");
            JsonObject json = GSON.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
            return getString(json, field, "").replace("\r", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String parseSongMid(String id) {
        if (id == null) {
            return "";
        }
        int split = id.indexOf(',');
        return split >= 0 ? id.substring(0, split).trim() : id.trim();
    }

    private static String parseMediaMid(String id) {
        if (id == null) {
            return "";
        }
        int split = id.indexOf(',');
        return split >= 0 ? id.substring(split + 1).trim() : "";
    }

    static String unavailableMessage(JsonObject selected, String cookie) {
        boolean paid = selected.has("paid") && selected.get("paid").getAsBoolean();
        boolean logged = !QQAccount.uin(cookie).equals("0") && !QQAccount.key(cookie).isEmpty();
        String title = getString(selected, "name", "这首歌");
        return "QQ音乐" + (paid ? "标记《" + title + "》为付费播放，" : "未提供《" + title + "》的有效播放地址，")
                + (logged ? "当前登录账号未取得该曲的音频授权，请检查会员、单独购买、地区权限及登录有效性。"
                : "当前未配置有效音乐登录凭据。管理员可使用 /zm login qq qr 扫码登录有播放权限的账号。")
                + "登录不会自动获得未购买的歌曲权限。";
    }

    private static String guid() {
        return String.valueOf(100000000 + new Random().nextInt(899999999));
    }

    private static String singers(JsonArray array) {
        if (array == null || array.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : array) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(getString(element.getAsJsonObject(), "name", ""));
        }
        return sb.toString();
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
        return value == null ? "" : value.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
    }
}
