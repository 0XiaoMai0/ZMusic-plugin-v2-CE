package me.zhenxin.zmusic.language;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;

/** 将音乐源和网络诊断转换为中文，不向玩家展示原始异常或响应内容。 */
public final class MusicErrorMessages {
    private MusicErrorMessages() { }

    public static String sourceName(String source) {
        if (source == null) return "音乐源";
        switch (source.toLowerCase(java.util.Locale.ROOT)) {
            case "163": case "netease": return "网易云音乐";
            case "qq": return "QQ音乐";
            case "kugou": return "酷狗音乐";
            case "kuwo": return "酷我音乐";
            case "bilibili": return "哔哩哔哩";
            default: return "音乐源";
        }
    }

    public static String audioStatus(String status) {
        if (status == null) return "音频检查失败，请稍后重试";
        if (status.startsWith("OK:")) return "已取得可播放的音频";
        if (status.equals("NO_TRACK")) return "未找到歌曲或音乐源请求失败";
        if (status.equals("NO_URL")) return "未取得播放地址";
        if (status.equals("INVALID_AUDIO")) return "音乐源返回的内容不是有效音频";
        if (status.startsWith("HTTP:") || status.matches("HTTP \\d{3}")) {
            String code = status.substring(5);
            if (code.equals("401") || code.equals("403")) return "音乐源拒绝访问（状态码：" + code + "），请检查账号权限或选择其他歌曲";
            if (code.equals("404") || code.equals("410")) return "音频地址已失效（状态码：" + code + "），请重新搜索歌曲";
            if (code.equals("429")) return "请求过于频繁（状态码：429），请稍后重试";
            if (code.matches("\\d{3}")) return "音乐源请求失败（状态码：" + code + "），请稍后重试";
        }
        if (status.contains("SocketTimeoutException") || status.contains("TimeoutException")) return "连接音乐源超时，请稍后重试";
        if (status.contains("UnknownHostException")) return "无法解析音乐源地址，请管理员检查服务器网络";
        if (status.contains("SSL")) return "安全连接失败，请管理员检查服务器证书和网络";
        if (status.startsWith("NETWORK:") || status.startsWith("ERROR:")) return "无法连接音乐源，请稍后重试或联系管理员";
        return "音频检查失败，请稍后重试或选择其他歌曲";
    }

    public static String requestFailure(Throwable error, String fallback) {
        // 已知网络异常优先于外层包装，避免把异常类名或签名地址发到聊天栏。
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof java.util.concurrent.TimeoutException) return audioStatus("NETWORK:SocketTimeoutException");
            if (current instanceof UnknownHostException) return audioStatus("NETWORK:UnknownHostException");
            if (current instanceof SSLException) return audioStatus("NETWORK:SSLException");
        }
        return remoteMessage(error == null ? null : error.getMessage(), fallback);
    }

    public static String remoteMessage(String message, String fallback) {
        if (message == null || message.trim().isEmpty()) return fallback;
        String text = message.trim();
        if (text.startsWith("HTTP ") || text.startsWith("HTTP:") || text.startsWith("NETWORK:")) return audioStatus(text);
        // 保留简短的中文说明和常用格式名称，其他原始响应只用于诊断。
        String words = text.replaceAll("(?i)\\b(?:HTTP|MP3|MP4|AAC|DASH|MiB|JSON|API|QQ)\\b", "");
        if (text.length() > 240 || !text.matches("(?s).*[\\u4e00-\\u9fff].*")
            || words.matches("(?s).*[A-Za-z]{3,}.*") || text.contains("=") || text.contains("://")) return fallback;
        return text;
    }
}
