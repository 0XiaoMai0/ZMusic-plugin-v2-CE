package me.zhenxin.zmusic.login;

import com.sun.net.httpserver.HttpExchange;
import me.zhenxin.zmusic.audio.ModAudioServer;
import me.zhenxin.zmusic.config.Config;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 在插件已有 HTTP 端点提供短期登录图片；不落盘、不交给第三方二维码服务。 */
public final class LoginImages {
    private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private LoginImages() { }
    public static synchronized String publish(QQLogin.Image image) throws IOException {
        if (image.bytes.length < 8 || image.bytes.length > 256 * 1024) throw new IOException("登录图片大小异常。");
        boolean png = image.mime.equals("image/png") && image.bytes[0] == (byte)137 && image.bytes[1] == 80 && image.bytes[2] == 78 && image.bytes[3] == 71;
        boolean jpeg = image.mime.equals("image/jpeg") && image.bytes[0] == (byte)255 && image.bytes[1] == (byte)216;
        if (!png && !jpeg) throw new IOException("登录接口未返回有效的二维码或验证码图片。");
        ModAudioServer.ensureLoginEndpoint();
        IMAGES.entrySet().removeIf(entry -> entry.getValue().expires < System.currentTimeMillis());
        if (IMAGES.size() >= 16) throw new IOException("登录会话过多，请稍后再试。");
        byte[] token = new byte[24]; RANDOM.nextBytes(token);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        IMAGES.put(id, new Image(image, System.currentTimeMillis() + 5 * 60 * 1000L));
        return Config.audioPublicUrl.replaceAll("/+$", "") + "/login-image/" + id;
    }
    public static void remove(String address) {
        if (address != null) IMAGES.remove(address.substring(address.lastIndexOf('/') + 1));
    }
    public static void clear() { IMAGES.clear(); }
    public static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            String path = exchange.getRequestURI().getPath();
            if (!path.matches("/login-image/[A-Za-z0-9_-]{32}")) { exchange.sendResponseHeaders(404, -1); return; }
            String token = path.substring("/login-image/".length());
            Image image = IMAGES.get(token);
            if (image == null || image.expires < System.currentTimeMillis()) { IMAGES.remove(token); exchange.sendResponseHeaders(410, -1); return; }
            exchange.getResponseHeaders().set("Content-Type", image.image.mime);
            exchange.getResponseHeaders().set("Cache-Control", "private, no-store");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(image.image.bytes.length));
            boolean head = "HEAD".equals(exchange.getRequestMethod());
            exchange.sendResponseHeaders(200, head ? -1 : image.image.bytes.length);
            if (!head) exchange.getResponseBody().write(image.image.bytes);
        } finally { exchange.close(); }
    }
    private static final class Image {
        final QQLogin.Image image; final long expires;
        Image(QQLogin.Image image, long expires) { this.image = image; this.expires = expires; }
    }
}
