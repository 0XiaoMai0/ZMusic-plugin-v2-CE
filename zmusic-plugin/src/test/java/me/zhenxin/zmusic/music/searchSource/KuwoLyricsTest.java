package me.zhenxin.zmusic.music.searchSource;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class KuwoLyricsTest {
    @Test void decodesPlatformCompressedChineseLyricsAndRejectsErrorPages() throws Exception {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        response.write("tp=content\r\nlrcx=0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        try (DeflaterOutputStream compressed = new DeflaterOutputStream(response)) {
            compressed.write("[00:01.123]<0,500>测试歌词\r\n".getBytes(Charset.forName("GB18030")));
        }
        assertEquals("[00:01.123]测试歌词\n", KuwoLyrics.decode(response.toByteArray()));
        assertEquals("", KuwoLyrics.decode("<html>Error</html>".getBytes(StandardCharsets.UTF_8)));
    }
}
