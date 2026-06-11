package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class WebMusicUtils {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ZMusic/" + ZMusic.thisVer;

    private WebMusicUtils() {
    }

    public static String get(String url, String referer, String cookie) throws IOException {
        HttpURLConnection con = open(url, referer, cookie);
        con.setRequestMethod("GET");
        return read(con);
    }

    public static String postJson(String url, String referer, String cookie, String json) throws IOException {
        HttpURLConnection con = open(url, referer, cookie);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.write(json.getBytes("UTF-8"));
        out.flush();
        out.close();
        return read(con);
    }

    public static boolean hasContentLength(String url, String referer, String cookie) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        try {
            HttpURLConnection con = open(url, referer, cookie);
            con.setRequestMethod("HEAD");
            int code = con.getResponseCode();
            long length = con.getContentLengthLong();
            con.disconnect();
            return code >= 200 && code < 300 && length != 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static HttpURLConnection open(String url, String referer, String cookie) throws IOException {
        ZMusic.log.sendDebugMessage(url);
        URL getUrl = new URL(url);
        HttpURLConnection con = (HttpURLConnection) getUrl.openConnection();
        con.setReadTimeout(20000);
        con.setConnectTimeout(5000);
        con.addRequestProperty("Charset", "UTF-8");
        con.addRequestProperty("User-Agent", UA);
        if (referer != null && !referer.isEmpty()) {
            con.addRequestProperty("Referer", referer);
        }
        if (cookie != null && !cookie.isEmpty()) {
            con.addRequestProperty("Cookie", cookie);
        }
        return con;
    }

    private static String read(HttpURLConnection con) throws IOException {
        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        if (is == null) {
            return "";
        }
        String s = OtherUtils.readInputStream(is);
        is.close();
        ZMusic.log.sendDebugMessage(s);
        return s;
    }
}
