package me.zhenxin.zmusic.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** 有界 HTTP 请求和音频探测，不把登录凭据或签名 URL 写入日志。 */
public final class WebMusicUtils {
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
    private WebMusicUtils() { }

    public static String get(String url, String referer, String cookie) throws IOException {
        HttpURLConnection connection = open(url, referer, cookie);
        return read(connection);
    }

    public static byte[] getBytes(String url, String referer, String cookie) throws IOException {
        return readBytes(open(url, referer, cookie));
    }

    public static String getWithHeaders(String url, String referer, String cookie, java.util.Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, referer, cookie);
        for (java.util.Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        return read(connection);
    }

    public static String postJson(String url, String referer, String cookie, String json) throws IOException {
        HttpURLConnection connection = open(url, referer, cookie);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return read(connection);
    }

    /** 兼容旧方法名；以小范围 GET 检查真实音频，不把缺少 Content-Length 当成失败。 */
    public static boolean hasContentLength(String url, String referer, String cookie) {
        return probeAudio(url, referer, cookie).startsWith("OK:");
    }

    public static String probeAudio(String url, String referer, String cookie) {
        HttpURLConnection connection = null;
        try {
            connection = open(url, referer, cookie);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=0-1023");
            int code = connection.getResponseCode();
            if (code != 200 && code != 206) return "HTTP:" + code;
            try (InputStream input = connection.getInputStream()) {
                byte[] head = new byte[1024];
                int size = 0, read;
                while (size < head.length && (read = input.read(head, size, head.length - size)) > 0) size += read;
                String format = audioFormat(head, size);
                return format.isEmpty() ? "INVALID_AUDIO" : "OK:" + format;
            }
        } catch (IOException | IllegalArgumentException error) {
            return "NETWORK:" + error.getClass().getSimpleName();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 从 MP3 帧和响应总长度估计时长；无法可靠识别时返回 -1。 */
    public static double probeMp3Seconds(String url) {
        HttpURLConnection connection = null;
        try {
            connection = open(url, null, null);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Range", "bytes=0-16383");
            int code = connection.getResponseCode();
            if (code != 200 && code != 206) return -1;
            long total = connection.getContentLengthLong();
            if (code == 206) {
                String range = connection.getHeaderField("Content-Range");
                if (range == null || !range.matches("bytes \\d+-\\d+/\\d+")) return -1;
                total = Long.parseLong(range.substring(range.lastIndexOf('/') + 1));
            }
            if (total <= 0) return -1;
            try (InputStream input = connection.getInputStream()) {
                byte[] prefix = new byte[16384]; int size = 0, read;
                while (size < prefix.length && (read = input.read(prefix, size, prefix.length - size)) > 0) size += read;
                return mp3Seconds(prefix, size, total);
            }
        } catch (IOException | RuntimeException error) { return -1; }
        finally { if (connection != null) connection.disconnect(); }
    }

    static double mp3Seconds(byte[] prefix, int size, long total) {
        int start = 0;
        if (size >= 10 && prefix[0] == 'I' && prefix[1] == 'D' && prefix[2] == '3') {
            start = 10 + ((prefix[6] & 127) << 21) + ((prefix[7] & 127) << 14) + ((prefix[8] & 127) << 7) + (prefix[9] & 127);
        }
        int[] bitrateV1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
        int[] bitrateV2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};
        int[] rates = {44100, 48000, 32000};
        int offset = start, firstBitrate = 0, frames = 0;
        while (offset + 4 < size && frames < 8) {
            int header = ((prefix[offset] & 255) << 24) | ((prefix[offset + 1] & 255) << 16)
                    | ((prefix[offset + 2] & 255) << 8) | (prefix[offset + 3] & 255);
            int version = (header >>> 19) & 3, layer = (header >>> 17) & 3, bit = (header >>> 12) & 15, frequency = (header >>> 10) & 3;
            if ((header & 0xffe00000) != 0xffe00000 || version == 1 || layer != 1 || bit == 0 || bit == 15 || frequency == 3) {
                if (frames > 0) return -1;
                offset++; continue;
            }
            int rate = rates[frequency] / (version == 3 ? 1 : version == 2 ? 2 : 4);
            int bitrate = (version == 3 ? bitrateV1 : bitrateV2)[bit];
            int samples = version == 3 ? 1152 : 576;
            int frameBytes = (version == 3 ? 144000 : 72000) * bitrate / rate + ((header >>> 9) & 1);
            if (offset + frameBytes > size) break;
            if (frames == 0) {
                // Xing/Info 的总帧数比 CBR 长度估算更可靠，尤其是 VBR 歌曲。
                int sideBytes = version == 3 ? ((header >>> 6 & 3) == 3 ? 17 : 32) : ((header >>> 6 & 3) == 3 ? 9 : 17);
                int marker = offset + 4 + ((header & 0x10000) == 0 ? 2 : 0) + sideBytes;
                if (marker + 12 <= offset + frameBytes) {
                    String name = new String(prefix, marker, 4, StandardCharsets.US_ASCII);
                    if ("Xing".equals(name) || "Info".equals(name)) {
                        java.nio.ByteBuffer box = java.nio.ByteBuffer.wrap(prefix).order(java.nio.ByteOrder.BIG_ENDIAN);
                        if ((box.getInt(marker + 4) & 1) != 0) {
                            long totalFrames = Integer.toUnsignedLong(box.getInt(marker + 8));
                            return totalFrames > 0 ? (double) totalFrames * samples / rate : -1;
                        }
                        return -1;
                    }
                }
                start = offset; firstBitrate = bitrate;
            } else if (firstBitrate != bitrate) return -1;
            frames++; offset += frameBytes;
        }
        return frames == 8 && total > start ? (double) (total - start) * 8 / (firstBitrate * 1000) : -1;
    }

    static String audioFormat(byte[] head, int size) {
        if (size >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') return "MP3";
        if (size >= 12) {
            String prefix = new String(head, 0, 12, StandardCharsets.ISO_8859_1);
            if (prefix.startsWith("RIFF") && prefix.endsWith("WAVE")) return "WAV";
            if (prefix.startsWith("fLaC")) return "FLAC";
            if (prefix.startsWith("OggS")) return "OGG";
            if (prefix.substring(4, 8).equals("ftyp")) return "M4A";
        }
        for (int index = 0; index + 3 < size; index++) {
            int second = head[index + 1] & 255;
            int third = head[index + 2] & 255;
            if ((head[index] & 255) == 255 && (second & 224) == 224
                    && (second & 24) != 8 && (second & 6) != 0
                    && (third & 240) != 0 && (third & 240) != 240 && (third & 12) != 12) return "MP3";
        }
        return "";
    }

    public static HttpURLConnection open(String url, String referer, String cookie) throws IOException {
        URL target = new URL(url);
        if (!"https".equals(target.getProtocol()) && !"http".equals(target.getProtocol())) {
            throw new IOException("Unsupported URL protocol");
        }
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", UA);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
        if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", ServiceCookieUtils.normalizeRawCookie(cookie));
        return connection;
    }

    private static String read(HttpURLConnection connection) throws IOException {
        return new String(readBytes(connection), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(HttpURLConnection connection) throws IOException {
        try {
            int code = connection.getResponseCode();
            InputStream raw = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (raw == null) throw new IOException("HTTP " + code);
            try (InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int size;
                while ((size = input.read(buffer)) > 0) {
                    if (output.size() + size > 4 * 1024 * 1024) throw new IOException("API response too large");
                    output.write(buffer, 0, size);
                }
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
