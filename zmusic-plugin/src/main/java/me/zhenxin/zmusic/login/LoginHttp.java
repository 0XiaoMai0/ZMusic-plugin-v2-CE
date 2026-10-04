package me.zhenxin.zmusic.login;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 每次登录独立的 Cookie 容器；不跟随外部重定向，不记录请求内容和凭据。 */
public class LoginHttp {
    private final CookieManager cookies = new CookieManager(null, LoginHttp::cookieScope);
    private String userAgent = WebMusicUtils.UA;

    public synchronized void androidAgent() { userAgent = "QQMusic 14090008(android 13)"; }

    public synchronized Response request(String method, String url, String referer, String body, String contentType) throws IOException {
        URI uri = URI.create(url);
        if (!allowed(uri)) throw new IOException("登录请求地址无效。");
        HttpURLConnection connection = WebMusicUtils.open(url, referer, null);
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setInstanceFollowRedirects(false);
        connection.setReadTimeout("lp.open.weixin.qq.com".equals(uri.getHost()) ? 35000 : 10000);
        connection.setRequestMethod(method);
        // 使用 RFC 6265 的域匹配，兼容 QQ 返回的 Domain=ptlogin2.qq.com；
        // JDK 的旧 RFC 2965 默认策略会拒绝 ssl.ptlogin2.qq.com 上的此类 Cookie。
        StringBuilder header = new StringBuilder();
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            String path = cookie.getPath() == null ? "/" : cookie.getPath();
            String target = uri.getPath().isEmpty() ? "/" : uri.getPath();
            if (cookie.hasExpired() || !cookieScope(uri, cookie) || !(target.equals(path) || target.startsWith(path.endsWith("/") ? path : path + "/"))) continue;
            if (header.length() != 0) header.append("; ");
            header.append(cookie.getName()).append('=').append(cookie.getValue());
        }
        if (header.length() != 0) connection.setRequestProperty("Cookie", header.toString());
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
            }
            int status = connection.getResponseCode();
            cookies.put(uri, connection.getHeaderFields());
            InputStream raw = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (raw != null) try (InputStream input = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
                byte[] bytes = new byte[4096];
                int count;
                while ((count = input.read(bytes)) != -1) {
                    if (buffer.size() + count > 1024 * 1024) throw new IOException("登录响应过大。");
                    buffer.write(bytes, 0, count);
                }
            }
            if (status >= 400) throw new IOException("登录服务请求失败（HTTP " + status + "）。");
            return new Response(status, buffer.toByteArray(), connection.getHeaderField("Location"));
        } finally { connection.disconnect(); }
    }

    public synchronized String cookie(String name) {
        for (HttpCookie cookie : cookies.getCookieStore().getCookies()) {
            if (!cookie.hasExpired() && cookie.getName().equals(name)) return cookie.getValue();
        }
        return "";
    }

    public static boolean cookieScope(URI uri, HttpCookie cookie) {
        String domain = cookie.getDomain(), host = uri.getHost();
        if (domain == null || host == null) return false;
        domain = domain.toLowerCase(java.util.Locale.ROOT).replaceFirst("^\\.", "");
        host = host.toLowerCase(java.util.Locale.ROOT);
        if (!(domain.equals("qq.com") || domain.endsWith(".qq.com") || domain.equals("weixin.qq.com")
                || domain.equals("kuwo.cn") || domain.endsWith(".kuwo.cn"))) return false;
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public static boolean allowed(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && host != null && (host.equals("ssl.ptlogin2.qq.com") || host.equals("ssl.ptlogin2.graph.qq.com")
                || host.equals("graph.qq.com") || host.equals("u.y.qq.com") || host.equals("y.qq.com")
                || host.equals("open.weixin.qq.com") || host.equals("lp.open.weixin.qq.com")
                || host.equals("www.kuwo.cn") || host.equals("wapi.kuwo.cn"));
    }

    public static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (Exception error) { throw new IllegalStateException("当前运行时不支持 UTF-8。"); }
    }

    public static String form(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((name, value) -> {
            if (result.length() != 0) result.append('&');
            result.append(encode(name)).append('=').append(encode(value));
        });
        return result.toString();
    }

    public static String query(String raw, String name) {
        if (raw == null || raw.isEmpty()) return "";
        String query = URI.create(raw).getRawQuery();
        if (query == null) return "";
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            try {
                if (parts.length == 2 && URLDecoder.decode(parts[0], "UTF-8").equals(name)) return URLDecoder.decode(parts[1], "UTF-8");
            } catch (Exception ignored) { return ""; }
        }
        return "";
    }

    public static Map<String, String> params(String... pairs) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put(pairs[index], pairs[index + 1]);
        return result;
    }

    public static final class Response {
        public final int status;
        public final byte[] bytes;
        public final String location;
        public Response(int status, byte[] bytes, String location) { this.status = status; this.bytes = bytes; this.location = location; }
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
        public JsonObject json() { return JsonParser.parseString(text()).getAsJsonObject(); }
    }
}
