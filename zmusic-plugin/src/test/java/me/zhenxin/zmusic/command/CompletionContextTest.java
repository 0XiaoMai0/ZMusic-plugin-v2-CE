package me.zhenxin.zmusic.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.data.PlayerData;
import me.zhenxin.zmusic.music.PlayList;
import me.zhenxin.zmusic.music.PlayListPlayer;
import me.zhenxin.zmusic.music.searchSource.SongMetadataCache;
import me.zhenxin.zmusic.utils.message.Message;
import me.zhenxin.zmusic.utils.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CompletionContextTest {
    @TempDir Path directory;
    final Object sender = new Object();
    final List<String> messages = new ArrayList<>();
    Player originalPlayer;
    Message originalMessage;
    File originalFolder;
    String originalApi;
    String name = "测试玩家";
    boolean admin, playAll;
    CompletionContext context;

    @BeforeEach void setup() {
        originalPlayer = ZMusic.player; originalMessage = ZMusic.message;
        originalFolder = ZMusic.dataFolder; originalApi = Config.neteaseApiRoot;
        ZMusic.dataFolder = directory.toFile();
        Config.neteaseApiRoot = "direct/";
        ZMusic.player = new Player() {
            public boolean hasPermission(Object player, String permission) {
                return permission.equals("zmusic.use") || permission.equals("zmusic.admin") && admin || permission.equals("zmusic.playall") && playAll;
            }
            public List<Object> getOnlinePlayerList() { throw new AssertionError("补全或拒绝未授权操作时不应访问玩家列表"); }
            public boolean isOnline(Object player) { return true; }
            public boolean isPlayer(Object player) { return player == sender; }
            public String getName(Object player) { return name; }
            public String getUniqueId(Object player) { return "test"; }
        };
        ZMusic.message = (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[]{Message.class}, (proxy, method, args) -> {
            if (args != null && args.length > 0 && args[0] instanceof String) messages.add((String) args[0]);
            return null;
        });
        context = new CompletionContext(sender);
    }

    @AfterEach void restore() {
        PlayerData.setPlayerPlayListPlayer(sender, null);
        ZMusic.player = originalPlayer; ZMusic.message = originalMessage;
        ZMusic.dataFolder = originalFolder; Config.neteaseApiRoot = originalApi;
    }

    private Path playlist(boolean global, String id, int count) throws Exception {
        Path folder = directory.resolve(global ? "playlist/global/netease" : "playlist/netease/" + name);
        Files.createDirectories(folder);
        JsonObject json = new JsonObject();
        JsonArray songs = new JsonArray();
        for (int i = 0; i < count; i++) songs.add(new JsonObject());
        json.add("list", songs);
        Path file = folder.resolve(id + ".json");
        Files.write(file, json.toString().getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test void importedIdsStayWithinPersonalAndGlobalDirectories() throws Exception {
        Path personal = playlist(false, "123", 21);
        playlist(true, "789", 10);
        playlist(false, "not-an-id", 2);
        Files.createDirectory(personal.getParent().resolve("456.json"));
        assertEquals(Arrays.asList("123"), context.playlistIds(false));
        assertEquals(Arrays.asList("789"), context.playlistIds(true));
        assertEquals(Arrays.asList("123"), Cmd.tab(sender, new String[]{"playlist", "163", "play", ""}));
        name = "另一位玩家";
        assertTrue(context.playlistIds(false).isEmpty());
        assertEquals(Arrays.asList("789"), context.playlistIds(true));
        name = "../";
        assertTrue(context.playlistIds(false).isEmpty());
        assertTrue(context.pageOffsets(false, "../123").isEmpty());
    }

    @Test void pagesTrackFileUpdatesWithoutNetworkRequests() throws Exception {
        playlist(false, "123", 21);
        assertEquals(Arrays.asList("0", "10", "20"), context.pageOffsets(false, "123"));
        assertEquals(Arrays.asList("0", "10", "20"), context.pageOffsets(false, "123"));
        playlist(false, "123", 1);
        assertEquals(Arrays.asList("0"), context.pageOffsets(false, "123"));
        playlist(false, "456", 0);
        assertEquals(Arrays.asList("0"), context.pageOffsets(false, "456"));
        assertTrue(context.pageOffsets(true, "123").isEmpty());
        assertTrue(context.pageOffsets(false, "../123").isEmpty());
        Path invalid = playlist(false, "789", 1);
        Files.write(invalid, "invalid".getBytes(StandardCharsets.UTF_8));
        assertTrue(context.pageOffsets(false, "789").isEmpty());
    }

    @Test void oversizedPlaylistOnlyOffersFirstPage() throws Exception {
        Path file = playlist(false, "123", 1);
        Files.write(file, new byte[2 * 1024 * 1024 + 1]);
        assertEquals(Arrays.asList("0"), context.pageOffsets(false, "123"));
    }

    @Test void currentPlaylistProvidesOneBasedNumbersAndItsId() {
        assertTrue(context.songNumbers().isEmpty());
        PlayListPlayer current = new PlayListPlayer();
        current.id = "123";
        current.playList = Arrays.asList(new JsonObject(), new JsonObject(), new JsonObject());
        PlayerData.setPlayerPlayListPlayer(sender, current);
        assertEquals(Arrays.asList("1", "2", "3"), context.songNumbers());
        assertEquals(Arrays.asList("123"), context.currentPlaylistId());
        assertEquals(Arrays.asList("2"), Cmd.tab(sender, new String[]{"playlist", "jump", "2"}));
    }

    @Test void cachedSongsPreservePlatformAndNeteaseAlias() {
        JsonArray songs = new JsonArray();
        JsonObject song = new JsonObject();
        song.addProperty("id", "completion-test-123");
        song.addProperty("name", "补全测试歌曲");
        songs.add(song);
        SongMetadataCache.remember("163", songs);
        assertNotNull(SongMetadataCache.get("netease", "completion-test-123"));
        assertTrue(context.songs("NETEASE", false).contains("补全测试歌曲"));
        assertTrue(context.songs("163", true).contains("-id:completion-test-123"));
        assertFalse(context.songs("qq", false).contains("补全测试歌曲"));
    }

    @Test void incompletePlaylistCommandsShowChineseHelpInsteadOfThrowing() {
        for (String[] args : new String[][] {{"playlist"}, {"playlist", "type"},
                {"playlist", "163", "play"}, {"playlist", "163", "show"},
                {"playlist", "global", "163", "play"}, {"playlist", "global", "163", "show"}}) {
            assertDoesNotThrow(() -> PlayList.subCommand(args, sender), Arrays.toString(args));
        }
        assertTrue(messages.stream().anyMatch(message -> message.contains("歌单")));
    }

    @Test void uppercasePlaylistModeExecutesAndDeniedPlayAllCannotResetPlayers() {
        PlayList.subCommand(new String[]{"playlist", "TYPE", "LOOP"}, sender);
        assertEquals("loop", PlayerData.getPlayerPlayListType(sender));
        PlayList.subCommand(new String[]{"playlist", "GLOBAL", "NETEASE", "PLAYALL", "789"}, sender);
        assertTrue(messages.stream().anyMatch(message -> message.contains("权限不足")));
    }

    @Test void suggestedJumpNumbersCanBeQueuedAndInvalidInputCannotChangePlayback() {
        PlayListPlayer current = new PlayListPlayer();
        current.id = "123";
        current.playList = Arrays.asList(new JsonObject(), new JsonObject(), new JsonObject());
        PlayerData.setPlayerPlayListPlayer(sender, current);
        for (String number : context.songNumbers()) {
            current.jumpMusic = false;
            PlayList.subCommand(new String[]{"playlist", "jump", number, "123"}, sender);
            assertTrue(current.jumpMusic, number);
            assertEquals(Integer.parseInt(number), current.jumpSong);
        }
        for (String number : Arrays.asList("", "abc", "0", "-1", "4")) {
            current.jumpMusic = false;
            PlayList.subCommand(new String[]{"playlist", "jump", number}, sender);
            assertFalse(current.jumpMusic, number);
        }
        current.jumpMusic = false;
        PlayList.subCommand(new String[]{"playlist", "jump", "1", "456"}, sender);
        assertFalse(current.jumpMusic);
    }
}
