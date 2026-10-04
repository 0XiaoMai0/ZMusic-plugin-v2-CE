package me.zhenxin.zmusic.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.data.PlayerData;
import me.zhenxin.zmusic.music.PlayListPlayer;
import me.zhenxin.zmusic.music.searchSource.SongMetadataCache;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 补全仅查询本地文件、当前播放状态和搜索缓存。 */
final class CompletionContext implements CommandCompletion.Context {
    private static final Map<String, Pages> PAGE_CACHE = new ConcurrentHashMap<>();
    private final Object sender;
    private final boolean player, use, admin, playAll;

    CompletionContext(Object sender) {
        this.sender = sender;
        player = ZMusic.player.isPlayer(sender);
        use = !player || ZMusic.player.hasPermission(sender, "zmusic.use");
        admin = !player || ZMusic.player.hasPermission(sender, "zmusic.admin");
        playAll = !player || ZMusic.player.hasPermission(sender, "zmusic.playall");
    }

    public boolean player() { return player; }
    public boolean use() { return use; }
    public boolean admin() { return admin; }
    public boolean playAll() { return playAll; }
    public boolean directNetease() { return "direct/".equals(Config.neteaseApiRoot); }

    private File directory(boolean global) {
        if (ZMusic.dataFolder == null || !player) return null;
        String name = ZMusic.player.getName(sender);
        if (name == null || name.contains("/") || name.contains("\\") || name.equals("..")) return null;
        return new File(ZMusic.dataFolder, global ? "playlist/global/netease" : "playlist/netease/" + name);
    }

    public List<String> playlistIds(boolean global) {
        File directory = directory(global);
        if (directory == null) return Collections.emptyList();
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().matches("\\d+\\.json"));
        if (files == null) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        for (File file : files) ids.add(file.getName().substring(0, file.getName().length() - 5));
        Collections.sort(ids);
        return ids;
    }

    public List<String> songNumbers() {
        PlayListPlayer current = PlayerData.getPlayerPlayListPlayer(sender);
        if (current == null || current.playList == null) return Collections.emptyList();
        List<String> numbers = new ArrayList<>();
        for (int i = 1; i <= current.playList.size(); i++) numbers.add(Integer.toString(i));
        return numbers;
    }

    public List<String> currentPlaylistId() {
        PlayListPlayer current = PlayerData.getPlayerPlayListPlayer(sender);
        return current == null || current.id == null ? Collections.emptyList() : Arrays.asList(current.id);
    }

    public List<String> pageOffsets(boolean global, String id) {
        File directory = directory(global);
        if (directory == null || !id.matches("\\d+")) return Collections.emptyList();
        File file = new File(directory, id + ".json");
        if (!file.isFile()) return Collections.emptyList();
        if (file.length() > 2 * 1024 * 1024) return Arrays.asList("0");
        String key = file.getAbsolutePath();
        long modified = file.lastModified(), size = file.length();
        Pages cached = PAGE_CACHE.get(key);
        if (cached != null && cached.modified == modified && cached.size == size) return cached.offsets;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            int count = json.getAsJsonArray("list").size();
            List<String> offsets = new ArrayList<>();
            for (int i = 0; i < Math.max(1, count); i += 10) offsets.add(Integer.toString(i));
            if (PAGE_CACHE.size() >= 128) PAGE_CACHE.clear();
            PAGE_CACHE.put(key, new Pages(modified, size, offsets));
            return offsets;
        } catch (Exception ignored) { return Collections.emptyList(); }
    }

    public List<String> noticeIds() {
        return ZMusic.notice == null ? Collections.emptyList() : ZMusic.notice.currentIds();
    }

    public List<String> songs(String source, boolean ids) { return SongMetadataCache.suggestions(source, ids); }

    private static final class Pages {
        final long modified, size;
        final List<String> offsets;
        Pages(long modified, long size, List<String> offsets) {
            this.modified = modified; this.size = size;
            this.offsets = Collections.unmodifiableList(offsets);
        }
    }
}
