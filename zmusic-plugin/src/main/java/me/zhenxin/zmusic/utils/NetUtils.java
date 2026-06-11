package me.zhenxin.zmusic.utils;

import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class NetUtils {

    private static final ThreadLocal<String> LAST_RESPONSE_COOKIE = new ThreadLocal<>();

    public static String getLastResponseCookie() {
        String cookie = LAST_RESPONSE_COOKIE.get();
        return cookie == null ? "" : cookie;
    }

    /**
     * 获取网络文件返回文本
     *
     * @param url 网络地址
     * @return 获取的文本
     */
    public static String getNetStringBiliBiliGZip(String url, String Referer) {
        ZMusic.log.sendDebugMessage(url);
        try {
            URL getUrl = new URL(url);
            HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
            con.setReadTimeout(20000);
            con.setConnectTimeout(5000);
            con.addRequestProperty("Charset", "UTF-8");
            con.addRequestProperty("Referer", Referer);
            con.addRequestProperty("User-Agent", "ZMusic/" + ZMusic.thisVer + " (service@iqianye.cn)");
            con.setRequestMethod("GET");
            int code = con.getResponseCode();
            if (code == 200 || code == 201 || code == 202) {
                GZIPInputStream gzipInputStream = new GZIPInputStream(con.getInputStream());
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
                String s = OtherUtils.readInputStream(inputStreamReader);
                gzipInputStream.close();
                inputStreamReader.close();
                return s;
            } else {
                GZIPInputStream gzipInputStream = new GZIPInputStream(con.getErrorStream());
                InputStreamReader inputStreamReader = new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8);
                String s = OtherUtils.readInputStream(inputStreamReader);
                gzipInputStream.close();
                inputStreamReader.close();
                return s;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取网络文件返回文本
     *
     * @param url 网络地址
     * @return 获取的文本
     */
    public static String getNetStringBiliBili(String url, String Referer) {
        ZMusic.log.sendDebugMessage(url);
        try {
            String ua = "ZMusic/" + ZMusic.thisVer + " (service@iqianye.cn)";
            return getString(url, Referer, ua);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getNetStringBiliBiliWeb(String url, String Referer) {
        ZMusic.log.sendDebugMessage(url);
        try {
            String ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ZMusic/" + ZMusic.thisVer;
            return getString(url, Referer, ua);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取网络文件返回文本
     *
     * @param url 网络地址
     * @return 获取的文本
     */
    public static String getNetString(String url, String Referer) {
        ZMusic.log.sendDebugMessage(url);
        try {
            String ua = "Mozilla/5.0 (Linux; Android 11; Mi 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.99 Mobile Safari/537.36 ZMusic/" + ZMusic.thisVer;
            return getString(url, Referer, ua);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取网络文件返回文本
     *
     * @param url 网络地址
     * @return 获取的文本
     */
    public static String postNetString(String url, String Referer, String content) {
        try {
            String ua = "Mozilla/5.0 (Linux; Android 11; Mi 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.99 Mobile Safari/537.36 ZMusic/" + ZMusic.thisVer;
            if (content == null) {
                content = "";
            }

            if (!url.contains("?")) {
                url = url + "?timestamp=" + System.currentTimeMillis();
            } else {
                url = url + "&timestamp=" + System.currentTimeMillis();
            }

            ZMusic.log.sendDebugMessage(url);
            ZMusic.log.sendDebugMessage(content);

            String neteaseCookie = "";
            boolean noCookieRequest = content.contains("noCookie=true");
            if (!noCookieRequest && url.contains(Config.neteaseApiRoot)) {
                ZMusic.log.sendDebugMessage("[NetUtils] 发送网易云音乐API请求，附加Cookie");
                String cookie = CookieUtils.getCookies();
                if (cookie != null && !cookie.isEmpty()) {
                    neteaseCookie = cookie;
                    String encodedCookie = URLEncoder.encode(cookie, "UTF-8");
                    content = content == null || content.isEmpty()
                        ? "cookie=" + encodedCookie
                        : content + "&cookie=" + encodedCookie;
                }
            }

            URL getUrl = new URL(url);
            HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
            con.setReadTimeout(20000);
            con.setConnectTimeout(5000);
            con.addRequestProperty("Charset", "UTF-8");
            con.addRequestProperty("Referer", Referer);
            con.addRequestProperty("User-Agent", ua);
            if (!neteaseCookie.isEmpty()) {
                con.setRequestProperty("Cookie", neteaseCookie);
            }
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setDoInput(true);
            con.connect();
            //DataOutputStream流
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            //将要上传的内容写入流中
            out.writeBytes(content);
            //刷新、关闭
            out.flush();
            out.close();
            return getString(con);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String postNetString(String url, String Referer, JsonObject data) {
        return postNetString(url, Referer, data, 20000);
    }

    public static String postNetString(String url, String Referer, JsonObject data, int readTimeout) {
        try {
            String ua = "Mozilla/5.0 (Linux; Android 11; Mi 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.99 Mobile Safari/537.36 ZMusic/" + ZMusic.thisVer;

            if (!url.contains("?")) {
                url = url + "?timestamp=" + System.currentTimeMillis();
            } else {
                url = url + "&timestamp=" + System.currentTimeMillis();
            }

            ZMusic.log.sendDebugMessage(url);

            URL getUrl = new URL(url);
            HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
            con.setReadTimeout(readTimeout);
            con.setConnectTimeout(5000);
            con.addRequestProperty("Charset", "UTF-8");
            con.addRequestProperty("Referer", Referer);
            con.addRequestProperty("User-Agent", ua);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setDoInput(true);
            con.connect();
            //DataOutputStream流
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            //将要上传的内容写入流中
            out.writeBytes(data.toString());
            //刷新、关闭
            out.flush();
            out.close();
            return getString(con);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private static String getString(String url, String Referer, String ua) throws IOException {
        URL getUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
        con.setReadTimeout(20000);
        con.setConnectTimeout(5000);
        con.addRequestProperty("Charset", "UTF-8");
        con.addRequestProperty("Referer", Referer);
        con.addRequestProperty("User-Agent", ua);
        con.setRequestMethod("GET");
        return getString(con);
    }

    private static String getString(HttpURLConnection con) throws IOException {
        LAST_RESPONSE_COOKIE.set(readSetCookie(con));
        int code = con.getResponseCode();
        if (code == 200 || code == 201 || code == 202) {
            InputStream is = con.getInputStream();
            String s = OtherUtils.readInputStream(is);
            is.close();
            ZMusic.log.sendDebugMessage(s);
            return s;
        } else {
            InputStream is = con.getErrorStream();
            String s = OtherUtils.readInputStream(is);
            is.close();
            ZMusic.log.sendDebugMessage(s);
            return s;
        }
    }

    private static String readSetCookie(HttpURLConnection con) {
        try {
            Map<String, List<String>> headers = con.getHeaderFields();
            if (headers == null || headers.isEmpty()) {
                return "";
            }
            StringBuilder cookie = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() == null || !"Set-Cookie".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                for (String value : entry.getValue()) {
                    if (value == null || value.isEmpty()) {
                        continue;
                    }
                    String first = value.split(";", 2)[0].trim();
                    if (first.isEmpty() || !first.contains("=")) {
                        continue;
                    }
                    if (cookie.length() > 0) {
                        cookie.append("; ");
                    }
                    cookie.append(first);
                }
            }
            return cookie.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

}
