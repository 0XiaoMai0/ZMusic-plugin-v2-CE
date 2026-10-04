package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.utils.OtherUtils;
import me.zhenxin.zmusic.utils.ServiceCookieUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import me.zhenxin.zmusic.language.MusicErrorMessages;

import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;

public class KugouMusic {

    // 旧公开搜索仅支持 HTTP，不携带账号 Cookie；登录和播放请求使用 HTTPS。
    private static final String SEARCH_API = "http://mobilecdn.kugou.com/api/v3/search/song";
    private static final String PLAY_API = "https://m.kugou.com/app/i/getSongInfo.php";
    private static final String WEB_PLAY_API = "https://wwwapi.kugou.com/yy/index.php";
    private static final String SIGNED_PLAY_API = "https://wwwapi.kugou.com/play/songinfo";
    private static final String SIGN_SECRET = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt";
    private static final String REFERER = "https://www.kugou.com/";
    private static final String DEVICE_MID = new java.math.BigInteger(OtherUtils.getMD5String(java.util.UUID.randomUUID().toString()), 16).toString();
    private static final String DEVICE_DFID = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(java.util.Locale.ROOT);

    public static JsonObject getMusicUrl(String musicName) {
        try {
            Gson gson = new GsonBuilder().create();
            JsonObject selected;
            if (musicName.contains("-id:")) {
                selected = new JsonObject();
                selected.addProperty("id", musicName.substring(musicName.indexOf("-id:") + 4).trim());
                selected.addProperty("name", selected.get("id").getAsString());
                selected.addProperty("singer", "");
                JsonObject cached = SongMetadataCache.get("kugou", selected.get("id").getAsString());
                if (cached != null) selected = cached;
            } else {
                JsonArray list = getMusicList(musicName);
                if (list == null || list.size() == 0) {
                    return null;
                }
                selected = list.get(0).getAsJsonObject();
            }

            String cookie = ServiceCookieUtils.getCookies("kugou");
            String id = selected.get("id").getAsString();
            KugouId kugouId = parseId(id, selected);
            String hash = kugouId.hash;
            String albumId = kugouId.albumId;
            JsonObject play = getSignedPlayInfo(gson, kugouId, cookie, false);
            JsonObject playData = getObject(play, "data");
            String musicUrl = getString(playData, "play_url", "");
            if (musicUrl.isEmpty() && !hash.isEmpty()) {
                JsonObject gateway = getGatewayPlayInfo(gson, kugouId, cookie);
                String gatewayUrl = getFirstArrayString(gateway, "url");
                if (!gatewayUrl.isEmpty()) { play = gateway; playData = gateway; musicUrl = gatewayUrl; }
            }
            if (musicUrl.isEmpty()) {
                musicUrl = getString(playData, "play_backup_url", "");
            }
            if (musicUrl.isEmpty()) {
                musicUrl = getFirstArrayString(playData, "backup_url");
            }

            String url = appendAuthParams(PLAY_API + "?cmd=playInfo&hash=" + URLEncoder.encode(hash, "UTF-8"), cookie);
            if (musicUrl.isEmpty()) {
                JsonObject mobilePlay = gson.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
                musicUrl = getString(mobilePlay, "url", "");
                if (musicUrl.isEmpty()) {
                    musicUrl = getBackupUrl(mobilePlay);
                }
                if (!musicUrl.isEmpty()) {
                    play = mobilePlay;
                    playData = getObject(play, "data");
                }
            }
            if (musicUrl.isEmpty()) {
                JsonObject webPlay = getWebPlayInfo(gson, hash, albumId, cookie);
                String webUrl = getString(getObject(webPlay, "data"), "play_url", "");
                if (!webUrl.isEmpty()) {
                    play = webPlay;
                    playData = getObject(play, "data");
                    musicUrl = webUrl;
                }
            }
            if (musicUrl.isEmpty()) {
                return errorResult(id, selected, play, "酷狗暂未提供这首歌的播放地址，请选择其他版本或联系管理员检查账号权限。");
            }
            if (!WebMusicUtils.hasContentLength(musicUrl, null, null)) {
                return errorResult(id, selected, play, MusicErrorMessages.audioStatus(WebMusicUtils.probeAudio(musicUrl, null, null)));
            }

            JsonObject result = new JsonObject();
            result.addProperty("id", id);
            result.addProperty("url", musicUrl);
            int time = getInt(play, "timeLength", getInt(play, "duration", 0));
            if (time == 0) {
                time = getInt(playData, "timelength", 0) / 1000;
            }
            if (time <= 0) time = getInt(selected, "time", 0);
            result.addProperty("time", time);
            result.addProperty("name", clean(getString(play, "songName", getString(playData, "song_name", selected.get("name").getAsString()))));
            result.addProperty("singer", clean(getString(play, "singerName", getString(playData, "author_name", selected.get("singer").getAsString()))));
            String lyric = getString(playData, "lyrics", "").replace("\r", "");
            result.addProperty("lyric", lyric.isEmpty() ? KugouLyrics.fetch(hash) : lyric);
            result.addProperty("lyricTr", getLyricTranslation(playData, play));
            result.addProperty("error", "");
            return result;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    public static JsonArray getMusicList(String musicName) {
        try {
            Gson gson = new GsonBuilder().create();
            String url = SEARCH_API +
                "?format=json&page=1&pagesize=10&keyword=" + URLEncoder.encode(musicName, "UTF-8");
            String cookie = ServiceCookieUtils.getCookies("kugou");
            JsonObject json = gson.fromJson(WebMusicUtils.get(url, REFERER, null), JsonObject.class);
            JsonObject data = json.getAsJsonObject("data");
            if (data == null || !data.has("info")) {
                return null;
            }

            JsonArray result = new JsonArray();
            for (JsonElement element : data.getAsJsonArray("info")) {
                JsonObject song = element.getAsJsonObject();
                String hash = getString(song, "hash", "");
                if (hash.isEmpty()) {
                    continue;
                }
                String albumId = getString(song, "album_id", getString(song, "albumid", ""));
                String albumAudioId = getString(song, "album_audio_id", "");
                String encodeAlbumAudioId = getString(song, "encode_album_audio_id", "");
                JsonObject item = new JsonObject();
                String id = encodeAlbumAudioId.isEmpty()
                    ? hash + "," + albumId + (albumAudioId.isEmpty() ? "" : "," + albumAudioId)
                    : "encode:" + encodeAlbumAudioId;
                item.addProperty("id", id);
                item.addProperty("hash", hash);
                item.addProperty("albumId", albumId);
                item.addProperty("albumAudioId", albumAudioId);
                item.addProperty("encodeAlbumAudioId", encodeAlbumAudioId);
                item.addProperty("name", clean(getString(song, "songname", hash)));
                item.addProperty("singer", clean(getString(song, "singername", "")));
                item.addProperty("time", getInt(song, "duration", 0));
                result.add(item);
            }
            SongMetadataCache.remember("kugou", result);
            return result;
        } catch (Exception e) {
            if (me.zhenxin.zmusic.ZMusic.log != null) me.zhenxin.zmusic.ZMusic.log.sendDebugMessage("音乐源请求失败: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static JsonObject getWebPlayInfo(Gson gson, String hash, String albumId, String cookie) {
        try {
            String url = WEB_PLAY_API + "?r=play/getdata&hash=" + URLEncoder.encode(hash, "UTF-8");
            if (albumId != null && !albumId.isEmpty()) {
                url += "&album_id=" + URLEncoder.encode(albumId, "UTF-8");
            }
            url = appendAuthParams(url, cookie);
            return gson.fromJson(WebMusicUtils.get(url, REFERER, cookie), JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 使用平台移动网关，并转发真实账号令牌；权限拒绝不作破解或替换歌曲。 */
    private static JsonObject getGatewayPlayInfo(Gson gson, KugouId id, String cookie) {
        try {
            Map<String, String> p = new TreeMap<>();
            String mid = getCookieOrKugoo(cookie, "KUGOU_API_MID", "");
            if (mid.isEmpty()) mid = getCookieOrKugoo(cookie, "mid", "");
            if (mid.isEmpty()) mid = getCookieOrKugoo(cookie, "kg_mid", "");
            if (mid.matches("[0-9a-fA-F]{32}") && !mid.matches("[0-9]+")) mid = new java.math.BigInteger(mid, 16).toString();
            if (mid.isEmpty()) mid = DEVICE_MID;
            String user = getCookieOrKugoo(cookie, "userid", "KugooID");
            if (user.isEmpty()) user = getCookieOrKugoo(cookie, "KugooID", "KugooID");
            if (user.isEmpty()) user = "0";
            String token = getCookieOrKugoo(cookie, "token", "t");
            if (token.isEmpty()) token = getCookieOrKugoo(cookie, "t", "t");
            String dfid = getCookieOrKugoo(cookie, "dfid", "");
            if (dfid.isEmpty()) dfid = getCookieOrKugoo(cookie, "kg_dfid", "");
            if (dfid.isEmpty()) dfid = DEVICE_DFID;
            p.put("appid", "1005"); p.put("clientver", "11430");
            p.put("clienttime", String.valueOf(System.currentTimeMillis() / 1000));
            p.put("mid", mid); p.put("uuid", "-"); p.put("dfid", dfid);
            p.put("hash", id.hash.toLowerCase(java.util.Locale.ROOT));
            p.put("album_id", id.albumId.isEmpty() ? "0" : id.albumId);
            p.put("album_audio_id", id.albumAudioId.isEmpty() ? "0" : id.albumAudioId);
            p.put("area_code", "1"); p.put("ssa_flag", "is_fromtrack");
            p.put("version", "11430"); p.put("page_id", "151369488"); p.put("quality", "128");
            p.put("behavior", "play"); p.put("pid", "2"); p.put("cmd", "26");
            p.put("pidversion", "3001"); p.put("IsFreePart", "0");
            p.put("ppage_id", "463467626,350369493,788954147"); p.put("cdnBackup", "1"); p.put("module", "");
            if (!"0".equals(user)) p.put("userid", user);
            if (!token.isEmpty()) p.put("token", token);
            p.put("key", OtherUtils.getMD5String(p.get("hash") + "57ae12eb6890223e355ccfcb74edf70d1005" + mid + user));
            String salt = "OIlwieks28dk2k092lksi2UIkp";
            StringBuilder raw = new StringBuilder(salt);
            for (Map.Entry<String, String> entry : p.entrySet()) raw.append(entry.getKey()).append('=').append(entry.getValue());
            p.put("signature", OtherUtils.getMD5String(raw.append(salt).toString()));
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, String> entry : p.entrySet()) {
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            java.net.HttpURLConnection connection = WebMusicUtils.open("https://gateway.kugou.com/v5/url?" + query, REFERER, null);
            connection.setRequestProperty("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi");
            connection.setRequestProperty("x-router", "trackercdn.kugou.com");
            connection.setRequestProperty("dfid", dfid); connection.setRequestProperty("mid", mid);
            connection.setRequestProperty("clienttime", p.get("clienttime"));
            connection.setRequestProperty("kg-rc", "1"); connection.setRequestProperty("kg-thash", "5d816a0");
            connection.setRequestProperty("kg-rec", "1"); connection.setRequestProperty("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");
            connection.setRequestProperty("Accept-Encoding", "identity");
            try (java.io.InputStream input = connection.getInputStream()) {
                return gson.fromJson(OtherUtils.readInputStream(input), JsonObject.class);
            } finally { connection.disconnect(); }
        } catch (Exception ignored) { return null; }
    }

    private static JsonObject getSignedPlayInfo(Gson gson, KugouId id, String cookie, boolean retry) {
        if (id == null || (!id.hasEncode() && id.hash.isEmpty() && id.albumAudioId.isEmpty())) {
            return null;
        }
        try {
            Map<String, String> params = signedBaseParams(cookie);
            params.put("platid", "4");
            if (id.hasEncode()) {
                params.put("encode_album_audio_id", id.encodeAlbumAudioId);
            } else {
                if (!id.hash.isEmpty()) {
                    params.put("hash", id.hash);
                }
                if (!id.albumId.isEmpty()) {
                    params.put("album_id", id.albumId);
                }
                if (!id.albumAudioId.isEmpty()) {
                    params.put("album_audio_id", id.albumAudioId);
                }
            }
            String endpoint = retry ? "https://wwwapiretry.kugou.com/play/songinfo" : SIGNED_PLAY_API;
            return gson.fromJson(WebMusicUtils.get(endpoint + "?" + signedQuery(params), REFERER, cookie), JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getBackupUrl(JsonObject play) {
        try {
            JsonObject backup = getObject(play, "backup_url");
            if (backup == null || backup.entrySet().isEmpty()) {
                return "";
            }
            return backup.entrySet().iterator().next().getValue().getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static JsonObject errorResult(String id, JsonObject selected, JsonObject play, String fallback) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("url", "");
        result.addProperty("time", 0);
        result.addProperty("name", selected == null ? id : getString(selected, "name", id));
        result.addProperty("singer", selected == null ? "" : getString(selected, "singer", ""));
        result.addProperty("lyric", KugouLyrics.fetch(parseId(id, selected).hash));
        result.addProperty("lyricTr", "");
        result.addProperty("error", "酷狗音乐播放失败：" + playbackError(play, fallback));
        return result;
    }

    static String playbackError(JsonObject play, String fallback) {
        int code = getInt(play, "err_code", 0);
        // 状态码优先，避免音乐源附带的英文说明覆盖中文提示。
        if (code == 30000) return "歌曲编号可能已失效（错误码：30000），请重新搜索并选择新的结果。";
        if (code == 30020) return "酷狗未提供这首歌的播放地址（错误码：30020）。请尝试其他版本，或联系管理员检查酷狗账号登录状态与歌曲权限。";
        if (code != 0) return "酷狗接口拒绝播放（错误码：" + code + "），请稍后重试或选择其他歌曲。";
        String error = getString(play, "error", getString(play, "error_msg", ""));
        JsonObject data = getObject(play, "data");
        if (error.isEmpty()) {
            error = getString(data, "error", getString(data, "error_msg", ""));
        }
        return MusicErrorMessages.remoteMessage(error, fallback);
    }

    private static KugouId parseId(String id, JsonObject selected) {
        KugouId result = new KugouId();
        if (id == null) {
            id = "";
        }
        if (id.startsWith("encode:")) {
            result.encodeAlbumAudioId = id.substring("encode:".length()).trim();
        } else {
            String[] parts = id.split(",");
            if (parts.length > 0) {
                result.hash = parts[0].trim();
            }
            if (parts.length > 1) {
                result.albumId = parts[1].trim();
            }
            if (parts.length > 2) {
                result.albumAudioId = parts[2].trim();
            }
        }
        if (selected != null) {
            if (result.hash.isEmpty()) {
                result.hash = getString(selected, "hash", "");
            }
            if (result.albumId.isEmpty()) {
                result.albumId = getString(selected, "albumId", "");
            }
            if (result.albumAudioId.isEmpty()) {
                result.albumAudioId = getString(selected, "albumAudioId", "");
            }
            if (result.encodeAlbumAudioId.isEmpty()) {
                result.encodeAlbumAudioId = getString(selected, "encodeAlbumAudioId", "");
            }
        }
        return result;
    }

    static Map<String, String> signedBaseParams(String cookie) {
        String now = String.valueOf(System.currentTimeMillis());
        Map<String, String> params = new TreeMap<>();
        params.put("appid", "1014");
        params.put("clientver", "20000");
        params.put("clienttime", now);
        params.put("srcappid", "2919");
        String mid = getCookieOrKugoo(cookie, "mid", "");
        if (mid.isEmpty()) {
            mid = getCookieOrKugoo(cookie, "kg_mid", "");
        }
        if (mid.isEmpty()) {
            mid = now;
        }
        params.put("mid", mid);
        params.put("uuid", mid);
        String dfid = getCookieOrKugoo(cookie, "dfid", "");
        if (dfid.isEmpty()) {
            dfid = getCookieOrKugoo(cookie, "kg_dfid", "");
        }
        params.put("dfid", dfid.isEmpty() ? "-" : dfid);
        String user = getCookieOrKugoo(cookie, "userid", "KugooID");
        if (user.isEmpty()) user = getCookieOrKugoo(cookie, "KugooID", "KugooID");
        String token = getCookieOrKugoo(cookie, "token", "t");
        if (token.isEmpty()) token = getCookieOrKugoo(cookie, "t", "t");
        params.put("userid", user);
        params.put("token", token);
        return params;
    }

    private static String signedQuery(Map<String, String> params) throws Exception {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder raw = new StringBuilder(SIGN_SECRET);
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            raw.append(entry.getKey()).append("=").append(entry.getValue() == null ? "" : entry.getValue());
        }
        raw.append(SIGN_SECRET);
        sorted.put("signature", OtherUtils.getMD5String(raw.toString()));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (query.length() > 0) {
                query.append("&");
            }
            query.append(URLEncoder.encode(entry.getKey(), "UTF-8"))
                .append("=")
                .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), "UTF-8"));
        }
        return query.toString();
    }

    private static String getCookieOrKugoo(String cookie, String cookieKey, String kugooKey) {
        String value = ServiceCookieUtils.getCookieValue(cookie, cookieKey);
        if (!value.isEmpty() || kugooKey == null || kugooKey.isEmpty()) {
            return value;
        }
        return getKugooValue(ServiceCookieUtils.getCookieValue(cookie, "KuGoo"), kugooKey);
    }

    static String appendAuthParams(String url, String cookie) throws Exception {
        String kugoo = ServiceCookieUtils.getCookieValue(cookie, "KuGoo");
        String userId = getCookieOrKugoo(cookie, "userid", "KugooID");
        if (userId.isEmpty()) userId = getCookieOrKugoo(cookie, "KugooID", "KugooID");
        String token = getCookieOrKugoo(cookie, "token", "t");
        if (token.isEmpty()) token = getCookieOrKugoo(cookie, "t", "t");
        String appId = getKugooValue(kugoo, "a_id");
        if (appId.isEmpty()) {
            appId = "1014";
        }
        url = appendParam(url, "appid", appId);
        url = appendParam(url, "clientver", "1000");
        url = appendParam(url, "platid", "4");
        url = appendParam(url, "srcappid", "2919");
        url = appendParam(url, "dfid", ServiceCookieUtils.getCookieValue(cookie, "kg_dfid"));
        String mid = ServiceCookieUtils.getCookieValue(cookie, "kg_mid");
        url = appendParam(url, "mid", mid);
        url = appendParam(url, "uuid", mid);
        url = appendParam(url, "userid", userId);
        url = appendParam(url, "token", token);
        return url;
    }

    private static String appendParam(String url, String key, String value) throws Exception {
        if (value == null || value.isEmpty() || url.contains("&" + key + "=") || url.contains("?" + key + "=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + key + "=" + URLEncoder.encode(value, "UTF-8");
    }

    private static String getKugooValue(String kugoo, String key) {
        if (kugoo == null || kugoo.isEmpty()) {
            return "";
        }
        String[] parts = kugoo.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return kv[1];
            }
        }
        return "";
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
            .trim();
    }

    private static String getFirstArrayString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonArray()) {
            return "";
        }
        JsonArray array = obj.getAsJsonArray(key);
        return array.size() == 0 ? "" : array.get(0).getAsString();
    }

    private static String getLyricTranslation(JsonObject data, JsonObject play) {
        String value = firstNonEmpty(data,
            "trans", "translation", "translate", "translated_lyrics", "translate_lyrics",
            "lyrics_trans", "lyrics_translate", "trans_lyrics", "tlyrics", "tlyric");
        if (value.isEmpty()) {
            value = firstNonEmpty(play,
                "trans", "translation", "translate", "translated_lyrics", "translate_lyrics",
                "lyrics_trans", "lyrics_translate", "trans_lyrics", "tlyrics", "tlyric");
        }
        return value.replace("\r", "");
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

    private static class KugouId {
        private String hash = "";
        private String albumId = "";
        private String albumAudioId = "";
        private String encodeAlbumAudioId = "";

        private boolean hasEncode() {
            return encodeAlbumAudioId != null && !encodeAlbumAudioId.isEmpty();
        }
    }
}
