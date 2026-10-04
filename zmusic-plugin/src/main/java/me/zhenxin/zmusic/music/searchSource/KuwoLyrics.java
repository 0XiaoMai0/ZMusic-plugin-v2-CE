package me.zhenxin.zmusic.music.searchSource;

import me.zhenxin.zmusic.utils.WebMusicUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

/** 酷我 H5 返回业务错误时，尝试平台原有的压缩 LRC 接口。 */
final class KuwoLyrics {
    private KuwoLyrics() { }

    static String fetch(String id) {
        if (!id.matches("[0-9]+")) return "";
        try {
            byte[] query = ("user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_" + id)
                    .getBytes(StandardCharsets.UTF_8);
            byte[] key = "yeelion".getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < query.length; i++) query[i] ^= key[i % key.length];
            byte[] response = WebMusicUtils.getBytes("https://newlyric.kuwo.cn/newlyric.lrc?"
                    + Base64.getEncoder().encodeToString(query), "https://www.kuwo.cn/", null);
            return decode(response);
        } catch (Exception ignored) { return ""; }
    }

    static String decode(byte[] response) throws java.io.IOException {
        if (response.length < 14 || !new String(response, 0, 10, StandardCharsets.US_ASCII).equals("tp=content")) return "";
        int offset = -1;
        for (int i = 0; i + 3 < response.length; i++) {
            if (response[i] == 13 && response[i + 1] == 10 && response[i + 2] == 13 && response[i + 3] == 10) {
                offset = i + 4; break;
            }
        }
        if (offset < 0) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = new InflaterInputStream(new ByteArrayInputStream(response, offset, response.length - offset))) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) > 0) {
                if (output.size() + count > 4 * 1024 * 1024) throw new java.io.IOException("Lyric response too large");
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), Charset.forName("GB18030")).replace("\r", "")
                .replaceAll("<-?\\d+,-?\\d+>", "");
    }
}
