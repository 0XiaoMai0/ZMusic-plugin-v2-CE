package me.zhenxin.zmusic.command;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CommandCompletionTest {
    private final Context context = new Context();
    private List<String> tab(String... args) { return CommandCompletion.complete(args, context); }

    @Test void completesRootsByPermissionAndPrefix() {
        assertTrue(tab().containsAll(Arrays.asList("play", "music", "search", "playlist", "help", "test")));
        assertFalse(tab().contains("login"));
        assertFalse(tab().contains("web"));
        assertEquals(Arrays.asList("play", "playlist"), tab("PL"));
        context.admin = true;
        assertTrue(tab("").containsAll(Arrays.asList("playAll", "stopAll", "login", "diagnose", "notice", "reload", "update")));
        assertEquals(Arrays.asList("play", "playlist", "playAll"), tab("pL"));
    }

    @Test void deniesNestedCommandsAndHonorsIndependentNoticePermission() {
        assertTrue(tab("login", "").isEmpty());
        assertTrue(tab("diagnose", "").isEmpty());
        assertTrue(tab("playAll", "").isEmpty());
        context.use = false;
        assertTrue(tab("").isEmpty());
        assertTrue(tab("play", "").isEmpty());
        context.admin = true;
        assertEquals(Arrays.asList("notice"), tab(""));
        assertEquals(Arrays.asList("read"), tab("notice", ""));
        assertEquals(Arrays.asList("20261004"), tab("notice", "READ", "2026"));
    }

    @Test void consoleOnlySeesCommandsItCanExecute() {
        context.player = false;
        context.admin = true;
        assertTrue(tab("").containsAll(Arrays.asList("help", "login", "diagnose", "playAll", "stopAll", "reload", "update")));
        for (String playerOnly : Arrays.asList("play", "music", "search", "stop", "loop", "playlist", "url", "test", "notice")) {
            assertFalse(tab("").contains(playerOnly));
            assertTrue(tab(playerOnly, "").isEmpty());
        }
        assertTrue(tab("playAll", "").contains("qq"));
    }

    @Test void completesEveryMusicSourceAndCachedSongsAtTheCorrectToken() {
        assertEquals(Arrays.asList("163", "netease", "qq", "kugou", "kuwo", "bilibili"), tab("play", ""));
        assertEquals(Arrays.asList("kugou", "kuwo"), tab("SEARCH", "KU"));
        assertEquals(Arrays.asList("晴天"), tab("play", "qq", "晴"));
        assertEquals(Arrays.asList("World Again"), tab("music", "QQ", "Hello", "W"));
        assertEquals(Arrays.asList("Again"), tab("search", "qq", "Hello", "World", "A"));
        assertTrue(tab("play", "unknown", "").isEmpty());
    }

    @Test void onlyPlaybackAndDiagnosticsOfferIds() {
        assertEquals(Arrays.asList("-id:123"), tab("play", "qq", "-ID:1"));
        assertTrue(tab("play", "qq", "").contains("-id:"));
        assertFalse(tab("search", "qq", "").contains("-id:"));
        context.admin = true;
        assertEquals(Arrays.asList("-id:123"), tab("diagnose", "qq", "-id:"));
        assertEquals(Arrays.asList("-id:123"), tab("playAll", "qq", "-id:"));
    }

    @Test void loginMatchesProviderAndConfiguredNeteaseMode() {
        context.admin = true;
        assertTrue(tab("login", "").containsAll(Arrays.asList("163", "netease", "qq", "kugou", "kuwo", "bilibili", "raw", "status")));
        assertFalse(tab("login", "").contains("qr"));
        for (String platform : Arrays.asList("kugou", "bilibili", "163", "netease")) {
            assertEquals(Arrays.asList("raw", "status"), tab("login", platform, ""));
        }
        assertTrue(tab("login", "qq", "").containsAll(Arrays.asList("qr", "sendcode", "verify", "raw", "status", "cancel")));
        assertEquals(Arrays.asList("qq", "wechat"), tab("login", "QQ", "QR", ""));
        assertEquals(Arrays.asList("+86", "+852", "+853", "+886"), tab("login", "qq", "sendcode", ""));
        assertTrue(tab("login", "kuwo", "").containsAll(Arrays.asList("captcha", "password", "sendcode", "verify", "raw", "status", "cancel")));
        assertFalse(tab("login", "kuwo", "").contains("qr"));
        context.direct = false;
        assertTrue(tab("login", "").containsAll(Arrays.asList("qr", "phone", "email", "sendcode", "verify")));
        assertTrue(tab("login", "163", "").contains("qr"));
        assertEquals(Arrays.asList("raw", "status"), tab("login", "kugou", ""));
        assertEquals(Arrays.asList("+86", "+852", "+853", "+886"), tab("login", "phone", "+8"));
        assertEquals(Arrays.asList("+86", "+852", "+853", "+886"), tab("login", "NETEASE", "VERIFY", ""));
    }

    @Test void neverSuggestsCredentialsOrExtraArguments() {
        context.admin = true;
        context.direct = false;
        for (String[] input : new String[][] {
                {"login", "raw", ""}, {"login", "kugou", "raw", ""},
                {"login", "netease", "raw", ""}, {"login", "status", ""},
                {"login", "email", ""}, {"login", "phone", "+86", "13800138000", ""},
                {"login", "verify", "+86", "13800138000", ""},
                {"login", "qq", "verify", "+86", "13800138000", ""},
                {"login", "kuwo", "password", "test-account", ""},
                {"login", "kuwo", "sendcode", "13800138000", ""},
                {"url", ""}, {"stop", ""}, {"reload", ""}, {"help", "main", ""},
                {"playlist", "next", ""}, {"playlist", "163", "import", ""},
                {"playlist", "global", "163", "list", ""}}) {
            assertTrue(tab(input).isEmpty(), Arrays.toString(input));
        }
    }

    @Test void personalPlaylistIdsPagesAndPlaybackStateHaveCorrectPositions() {
        assertEquals(Arrays.asList("play", "list", "show", "import", "update"), tab("playlist", "163", ""));
        assertEquals(Arrays.asList("123", "456"), tab("playlist", "NETEASE", "PLAY", ""));
        assertEquals(Arrays.asList("0", "10", "20"), tab("playlist", "netease", "show", "123", ""));
        assertEquals(Arrays.asList("normal", "loop", "random"), tab("playlist", "type", ""));
        assertEquals(Arrays.asList("1", "2", "3"), tab("playlist", "jump", ""));
        assertEquals(Arrays.asList("123"), tab("playlist", "jump", "2", ""));
        assertTrue(tab("playlist", "qq", "").isEmpty());
    }

    @Test void globalPlayAllAndMutationsRequireTheirOwnPermissions() {
        assertEquals(Arrays.asList("163", "netease"), tab("playlist", "GLOBAL", ""));
        assertEquals(Arrays.asList("play", "list", "show"), tab("playlist", "global", "163", ""));
        assertEquals(Arrays.asList("789"), tab("playlist", "global", "163", "play", ""));
        assertTrue(tab("playlist", "global", "163", "import", "").isEmpty());
        assertTrue(tab("playlist", "global", "163", "playall", "").isEmpty());
        context.playAll = true;
        assertTrue(tab("").contains("playAll"));
        assertEquals(Arrays.asList("play", "list", "show", "playall"), tab("playlist", "global", "163", ""));
        assertEquals(Arrays.asList("789"), tab("playlist", "global", "163", "playall", ""));
        context.admin = true;
        assertTrue(tab("playlist", "global", "163", "").containsAll(Arrays.asList("import", "update", "playall")));
        assertEquals(Arrays.asList("0", "10", "20"), tab("playlist", "global", "163", "show", "789", ""));
    }

    @Test void allPartialPathsSafelyReturnLists() {
        context.admin = true;
        for (String root : tab("")) {
            assertNotNull(tab(root, ""));
            for (String branch : tab(root, "")) {
                assertNotNull(tab(root, branch, ""));
                for (String option : tab(root, branch, "")) assertNotNull(tab(root, branch, option, ""));
            }
        }
        assertFalse(tab("help", "").isEmpty());
        context.admin = false;
        assertFalse(tab("help", "").contains("admin"));
    }

    private static final class Context implements CommandCompletion.Context {
        boolean player = true, use = true, admin, playAll, direct = true;
        public boolean player() { return player; }
        public boolean use() { return use; }
        public boolean admin() { return admin; }
        public boolean playAll() { return playAll; }
        public boolean directNetease() { return direct; }
        public List<String> playlistIds(boolean global) { return global ? Arrays.asList("789") : Arrays.asList("123", "456"); }
        public List<String> songNumbers() { return Arrays.asList("1", "2", "3"); }
        public List<String> currentPlaylistId() { return Arrays.asList("123"); }
        public List<String> pageOffsets(boolean global, String id) { return Arrays.asList("0", "10", "20"); }
        public List<String> noticeIds() { return Arrays.asList("20261004"); }
        public List<String> songs(String source, boolean ids) {
            return source.equalsIgnoreCase("qq") ? ids ? Arrays.asList("-id:123") : Arrays.asList("晴天", "Hello World Again") : Collections.emptyList();
        }
    }
}
