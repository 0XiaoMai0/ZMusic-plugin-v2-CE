package me.zhenxin.zmusic.audio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.utils.WebMusicUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 为现有游戏模组提供 MP3 地址；不提供网页，不启动外部程序，不保存音频文件。 */
public final class ModAudioServer {
    private static final Object LOCK = new Object();
    private static final long TTL = 30 * 60 * 1000L;
    private static final long MAX_CACHE = 64 * 1024 * 1024L;
    private static final LinkedHashMap<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, Entry> TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Entry>> PENDING = new ConcurrentHashMap<>();
    private static final Semaphore CONVERSIONS = new Semaphore(2);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile HttpServer server;
    private static ThreadPoolExecutor executor;
    private static long bytes;
    private static int generation;
    private ModAudioServer() { }

    public static void start() {
        close();
        if (!Config.audioEnabled) return;
        startEndpoint();
    }

    public static synchronized void ensureLoginEndpoint() throws IOException {
        if (!available()) startEndpoint();
        if (!available()) throw new IOException("登录图片服务不可用，请检查 audio-stream 地址和端口。");
    }

    private static void startEndpoint() {
        try {
            URI publicUrl = URI.create(Config.audioPublicUrl);
            if ((!"http".equals(publicUrl.getScheme()) && !"https".equals(publicUrl.getScheme()))
                    || publicUrl.getHost() == null || publicUrl.getUserInfo() != null || publicUrl.getQuery() != null
                    || publicUrl.getFragment() != null || !(publicUrl.getPath().isEmpty() || "/".equals(publicUrl.getPath()))) {
                throw new IOException("audio-stream.public-url 必须是 HTTP(S) 根地址");
            }
            HttpServer created = HttpServer.create(new InetSocketAddress(Config.audioBind, Config.audioPort), 32);
            executor = new ThreadPoolExecutor(2, 16, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
                Thread thread = new Thread(task, "ZMusic-ModAudio"); thread.setDaemon(true); return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
            created.setExecutor(executor);
            created.createContext("/login-image/", me.zhenxin.zmusic.login.LoginImages::handle);
            created.createContext("/", ModAudioServer::handle);
            created.start();
            server = created;
            ZMusic.log.sendNormalMessage("游戏模组音频与登录图片服务已启动，端口 " + Config.audioPort + "。");
        } catch (Exception error) {
            close();
            ZMusic.log.sendErrorMessage("游戏模组音频服务启动失败: " + error.getClass().getSimpleName() + "，请检查 audio-stream 配置与端口。");
        }
    }

    public static void close() {
        synchronized (LOCK) {
            generation++;
            if (server != null) server.stop(0);
            if (executor != null) executor.shutdownNow();
            server = null; executor = null;
            CACHE.clear(); TOKENS.clear(); bytes = 0;
            me.zhenxin.zmusic.login.LoginImages.clear();
            PENDING.values().forEach(future -> future.completeExceptionally(new IOException("音频服务已停止")));
            PENDING.clear();
        }
    }

    public static boolean available() { return server != null; }

    public static String prepare(String id, String audio) throws IOException {
        if (!Config.audioEnabled || !available()) throw new IOException("请启用 audio-stream 游戏模组音频服务");
        if (!allowedMediaUrl(audio)) throw new IOException("B 站音频地址无效");
        CompletableFuture<Entry> future;
        boolean owner;
        int epoch;
        synchronized (LOCK) {
            evictExpired();
            Entry cached = CACHE.get(id);
            if (cached != null) { cached.lastAccess = System.currentTimeMillis(); return address(cached); }
            CompletableFuture<Entry> candidate = new CompletableFuture<>();
            future = PENDING.putIfAbsent(id, candidate);
            owner = future == null;
            if (owner) future = candidate;
            epoch = generation;
        }
        if (owner) {
            boolean acquired = CONVERSIONS.tryAcquire();
            try {
                if (!acquired) throw new IOException("已有两首 B 站歌曲正在适配，请稍后重试");
                byte[] mp3 = AacToMp3.convert(download(id, audio));
                byte[] token = new byte[24]; RANDOM.nextBytes(token);
                Entry entry = new Entry(id, Base64.getUrlEncoder().withoutPadding().encodeToString(token), mp3);
                synchronized (LOCK) {
                    if (epoch != generation || !available()) throw new IOException("音频服务已重新加载，请重新点歌");
                    evictExpired();
                    Iterator<Map.Entry<String, Entry>> iterator = CACHE.entrySet().iterator();
                    while ((bytes + mp3.length > MAX_CACHE || CACHE.size() >= 16) && iterator.hasNext()) {
                        Entry old = iterator.next().getValue(); iterator.remove(); TOKENS.remove(old.token); bytes -= old.audio.length;
                    }
                    CACHE.put(id, entry); TOKENS.put(entry.token, entry); bytes += mp3.length;
                }
                future.complete(entry);
            } catch (IOException | RuntimeException error) {
                future.completeExceptionally(error);
            } finally {
                if (acquired) CONVERSIONS.release();
                PENDING.remove(id, future);
            }
        }
        try { return address(future.get(120, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("音频适配已取消"); }
        catch (Exception error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("音频适配失败: " + (cause == null ? error : cause).getClass().getSimpleName());
        }
    }

    private static String address(Entry entry) { return Config.audioPublicUrl.replaceAll("/+$", "") + "/audio/" + entry.token + ".mp3"; }

    private static void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Entry> iterator = CACHE.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now - entry.lastAccess <= TTL) continue;
            iterator.remove(); TOKENS.remove(entry.token); bytes -= entry.audio.length;
        }
    }

    private static byte[] download(String id, String raw) throws IOException {
        String target = raw;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        for (int redirect = 0; redirect < 5; redirect++) {
            if (!allowedMediaUrl(target)) throw new IOException("B 站媒体重定向地址无效");
            HttpURLConnection connection = WebMusicUtils.open(target, "https://www.bilibili.com/video/" + id, null);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("Origin", "https://www.bilibili.com");
            try {
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("B 站媒体重定向缺少地址");
                    target = URI.create(target).resolve(location).toString(); continue;
                }
                if (code != 200) throw new IOException("B 站音频 HTTP " + code);
                if (connection.getContentLengthLong() > AacToMp3.MAX_BYTES) throw new IOException("B 站音频超过 24 MiB 限制");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("B 站音频请求已取消或超时");
                        if (output.size() + count > AacToMp3.MAX_BYTES) throw new IOException("B 站音频超过 24 MiB 限制");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("B 站媒体重定向次数过多");
    }

    public static boolean allowedMediaUrl(String raw) {
        try {
            URI uri = URI.create(raw); String host = uri.getHost(); int port = uri.getPort();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || !(port == -1 || port == 443 || port == 8082)) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.endsWith(".bilivideo.com") || host.endsWith(".bilivideo.cn") || host.endsWith(".bilibili.com") || host.endsWith(".hdslb.com");
        } catch (RuntimeException error) { return false; }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) { exchange.sendResponseHeaders(405, -1); return; }
            String path = exchange.getRequestURI().getPath();
            if (!path.matches("/audio/[A-Za-z0-9_-]{32}\\.mp3")) { exchange.sendResponseHeaders(404, -1); return; }
            Entry entry = TOKENS.get(path.substring(7, path.length() - 4));
            if (entry == null || System.currentTimeMillis() - entry.lastAccess > TTL) { exchange.sendResponseHeaders(410, -1); return; }
            entry.lastAccess = System.currentTimeMillis();
            byte[] audio = entry.audio;
            long[] range = byteRange(exchange.getRequestHeaders().getFirst("Range"), audio.length);
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
            exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            if (range == null) {
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + audio.length);
                exchange.sendResponseHeaders(416, -1); return;
            }
            int offset = (int) range[0], length = (int) (range[1] - range[0] + 1);
            int code = exchange.getRequestHeaders().getFirst("Range") == null ? 200 : 206;
            if (code == 206) exchange.getResponseHeaders().set("Content-Range", "bytes " + range[0] + "-" + range[1] + "/" + audio.length);
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(length));
            exchange.sendResponseHeaders(code, "HEAD".equals(method) ? -1 : length);
            if (!"HEAD".equals(method)) exchange.getResponseBody().write(audio, offset, length);
        } catch (IOException error) {
            // 模组停止或换歌时可主动断开下载，无需记录音频令牌。
        } finally { exchange.close(); }
    }

    static long[] byteRange(String value, int length) {
        if (value == null) return new long[]{0, length - 1};
        if (!value.matches("bytes=\\d*-\\d*")) return null;
        String[] parts = value.substring(6).split("-", -1);
        try {
            long start, end;
            if (parts[0].isEmpty()) {
                long suffix = Long.parseLong(parts[1]); if (suffix <= 0) return null;
                start = Math.max(0, length - suffix); end = length - 1;
            } else {
                start = Long.parseLong(parts[0]); end = parts[1].isEmpty() ? length - 1 : Math.min(Long.parseLong(parts[1]), length - 1);
            }
            return start >= 0 && start < length && end >= start ? new long[]{start, end} : null;
        } catch (NumberFormatException error) { return null; }
    }

    private static final class Entry {
        final String id, token;
        final byte[] audio;
        volatile long lastAccess = System.currentTimeMillis();
        Entry(String id, String token, byte[] audio) { this.id = id; this.token = token; this.audio = audio; }
    }
}
