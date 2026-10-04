package me.zhenxin.zmusic.music.searchSource;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.Locale;
/** 暂存搜索结果，按 ID 播放时保留歌曲名、歌手和平台关联 ID。 */
public final class SongMetadataCache {
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final long TTL = 10 * 60 * 1000L;
    private SongMetadataCache() { }
    public static void remember(String source, JsonArray songs) {
        source = canonical(source);
        CACHE.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().created > TTL);
        if (CACHE.size() > 2048) CACHE.clear();
        for (JsonElement element : songs) {
            JsonObject song = element.getAsJsonObject();
            if (song.has("id")) CACHE.put(source + ":" + song.get("id").getAsString(), new Entry(song.deepCopy()));
        }
    }
    public static JsonObject get(String source, String id) {
        source = canonical(source);
        Entry entry = CACHE.get(source + ":" + id);
        return entry == null || System.currentTimeMillis() - entry.created > TTL ? null : entry.song.deepCopy();
    }
    public static List<String> suggestions(String source, boolean ids) {
        String prefix = canonical(source) + ":";
        TreeSet<String> values = new TreeSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Entry> item : CACHE.entrySet()) {
            if (!item.getKey().startsWith(prefix) || now - item.getValue().created > TTL) continue;
            JsonObject song = item.getValue().song;
            String field = ids ? "id" : "name";
            if (!song.has(field) || song.get(field).isJsonNull()) continue;
            String value = song.get(field).getAsString().replaceAll("[\\r\\n\\t]", " ").trim();
            if (!value.isEmpty()) values.add((ids ? "-id:" : "") + value);
        }
        return new ArrayList<>(values);
    }
    private static String canonical(String source) {
        return source.equalsIgnoreCase("163") ? "netease" : source.toLowerCase(Locale.ROOT);
    }
    private static final class Entry {
        final long created = System.currentTimeMillis(); final JsonObject song;
        Entry(JsonObject song) { this.song = song; }
    }
}
