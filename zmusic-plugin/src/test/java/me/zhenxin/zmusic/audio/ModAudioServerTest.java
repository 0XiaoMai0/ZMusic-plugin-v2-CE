package me.zhenxin.zmusic.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModAudioServerTest {
    @Test void servesTheRangesRequestedByNativePlayers() {
        assertArrayEquals(new long[]{0, 99}, ModAudioServer.byteRange(null, 100));
        assertArrayEquals(new long[]{0, 99}, ModAudioServer.byteRange("bytes=0-1023", 100));
        assertArrayEquals(new long[]{20, 99}, ModAudioServer.byteRange("bytes=20-", 100));
        assertArrayEquals(new long[]{80, 99}, ModAudioServer.byteRange("bytes=-20", 100));
        assertArrayEquals(new long[]{0, 99}, ModAudioServer.byteRange("bytes=-999", 100));
    }
    @Test void rejectsInvalidRangesWithoutAllocatingBuffers() {
        for (String range : new String[]{"bytes=100-", "bytes=50-20", "bytes=-0", "bytes=-", "bytes=0-1,5-6", "bytes=999999999999999999999999-", "items=0-5"}) {
            assertNull(ModAudioServer.byteRange(range, 100), range);
        }
    }
    @Test void acceptsOnlyBilibiliHttpsMediaHosts() {
        assertTrue(ModAudioServer.allowedMediaUrl("https://upos-sz-mirrorcos.bilivideo.com/audio.m4s"));
        assertTrue(ModAudioServer.allowedMediaUrl("https://cn-example.mcdn.bilivideo.cn:8082/audio.m4s"));
        for (String url : new String[]{"http://example.bilivideo.com/test", "https://localhost/test", "https://example.bilivideo.com.attacker.test/x", "https://user:pass@example.bilivideo.com/x", "https://example.bilivideo.com:22/x", "file:///test", "invalid"}) {
            assertFalse(ModAudioServer.allowedMediaUrl(url), url);
        }
    }
    @Test void rejectsTruncatedAudioInsteadOfReturningAnEmptyMp3() {
        assertThrows(java.io.IOException.class, () -> AacToMp3.convert(new byte[0]));
        assertThrows(java.io.IOException.class, () -> AacToMp3.convert(new byte[]{0, 0, 0, 8, 'f', 't', 'y', 'p'}));
        assertThrows(java.io.IOException.class, () -> AacToMp3.convert("<html>access denied</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
