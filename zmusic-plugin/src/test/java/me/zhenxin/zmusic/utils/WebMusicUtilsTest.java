package me.zhenxin.zmusic.utils;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class WebMusicUtilsTest {
    @Test void acceptsChunkedMp3AndRejectsHtmlErrorsWithSuccessfulStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mp3", exchange -> {
            assertEquals("bytes=0-1023", exchange.getRequestHeaders().getFirst("Range"));
            byte[] bytes = new byte[]{'I','D','3',4,0,0,0,0,0,0};
            exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/html", exchange -> {
            byte[] bytes = "<html>403 Forbidden</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals("OK:MP3", WebMusicUtils.probeAudio(base + "/mp3", null, null));
            assertEquals("INVALID_AUDIO", WebMusicUtils.probeAudio(base + "/html", null, null));
        } finally { server.stop(0); }
    }
    @Test void identifiesContainerHeaders() {
        byte[] data = new byte[]{0,0,0,24,'f','t','y','p','i','s','o','m'};
        assertEquals("M4A", WebMusicUtils.audioFormat(data, data.length));
        assertFalse(WebMusicUtils.hasContentLength("file:///tmp/music.mp3", null, null));
    }
    @Test void estimatesCbrDurationAndAvoidsGuessingVariableBitrateAudio() {
        byte[] frames = new byte[8 * 417];
        for (int offset = 0; offset < frames.length; offset += 417) {
            frames[offset] = (byte) 0xff; frames[offset + 1] = (byte) 0xfb; frames[offset + 2] = (byte) 0x90;
        }
        assertEquals(200, WebMusicUtils.mp3Seconds(frames, frames.length, 3200000), 0.001);
        frames[417 + 2] = (byte) 0xa0;
        assertEquals(-1, WebMusicUtils.mp3Seconds(frames, frames.length, 3200000));
        assertEquals(-1, WebMusicUtils.mp3Seconds(new byte[10], 10, 3200000));
    }
}
