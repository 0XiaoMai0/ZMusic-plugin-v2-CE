package me.zhenxin.zmusic.music.searchSource;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/** 歌词使用独立接口，避免播放网关未附带 lyrics 时丢失歌词。 */
final class KugouLyrics {
    private KugouLyrics() { }

    static String fetch(String hash) {
        if (hash == null || !hash.matches("[0-9a-fA-F]{32}")) return "";
        try {
            String search = "https://lyrics.kugou.com/search?ver=1&man=no&client=pc&timelength=0&hash="
                    + hash.toUpperCase(Locale.ROOT);
            JsonObject response = JsonParser.parseString(WebMusicUtils.get(search, null, null)).getAsJsonObject();
            JsonArray candidates = response.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) return "";
            JsonObject candidate = candidates.get(0).getAsJsonObject();
            String url = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id="
                    + URLEncoder.encode(candidate.get("id").getAsString(), "UTF-8") + "&accesskey="
                    + URLEncoder.encode(candidate.get("accesskey").getAsString(), "UTF-8");
            JsonObject download = JsonParser.parseString(WebMusicUtils.get(url, null, null)).getAsJsonObject();
            if (!download.has("content") || download.get("content").isJsonNull()) return "";
            return new String(Base64.getDecoder().decode(download.get("content").getAsString()), StandardCharsets.UTF_8)
                    .replace("\r", "");
        } catch (Exception ignored) { return ""; }
    }
}
