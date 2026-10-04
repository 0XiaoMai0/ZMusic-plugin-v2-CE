package me.zhenxin.zmusic.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.music.searchSource.*;
import me.zhenxin.zmusic.utils.OtherUtils;
import me.zhenxin.zmusic.utils.WebMusicUtils;
import me.zhenxin.zmusic.language.MusicErrorMessages;
import java.io.File;
import java.util.Locale;

/** 分别验证搜索、真实音频字节和可解析歌词，不把获取 URL 当成客户端播放成功。 */
public final class SourceDiagnostics {
    private SourceDiagnostics() { }
    public static JsonObject inspect(String source, String keyword) {
        JsonObject report = new JsonObject();
        source = source.toLowerCase(Locale.ROOT);
        report.addProperty("source", source);
        report.addProperty("timestamp", System.currentTimeMillis());
        try {
            JsonArray songs;
            switch (source) {
                case "163": case "netease": songs = NeteaseCloudMusic.getMusicList(keyword); break;
                case "qq": songs = QQMusic.getMusicList(keyword); break;
                case "kugou": songs = KugouMusic.getMusicList(keyword); break;
                case "kuwo": songs = KuwoMusic.getMusicList(keyword); break;
                case "bilibili": songs = BiliBiliMusic.getMusicList(keyword); break;
                default: throw new IllegalArgumentException("未知音乐源");
            }
            report.addProperty("searchCount", songs == null ? 0 : songs.size());
            String key = keyword.startsWith("-id:") || keyword.matches("^BV[0-9A-Za-z]+$") || songs == null || songs.size() == 0
                    ? keyword : "-id:" + songs.get(0).getAsJsonObject().get("id").getAsString();
            JsonObject track;
            switch (source) {
                case "163": case "netease": track = NeteaseCloudMusic.getMusicUrl(key); break;
                case "qq": track = QQMusic.getMusicUrl(key); break;
                case "kugou": track = KugouMusic.getMusicUrl(key); break;
                case "kuwo": track = KuwoMusic.getMusicUrl(key); break;
                default: track = BiliBiliMusic.getMusic(key);
            }
            if (track == null) { report.addProperty("audio", "NO_TRACK"); return report; }
            report.addProperty("title", track.get("name").getAsString());
            report.addProperty("id", track.get("id").getAsString());
            String url = track.has("url") && !track.get("url").isJsonNull() ? track.get("url").getAsString() : "";
            report.addProperty("audio", url.isEmpty() ? "NO_URL" : WebMusicUtils.probeAudio(url, null, null));
            report.addProperty("lyricLines", LyricParser.parseSeconds(track.get("lyric").getAsString()).size());
            report.addProperty("translatedLines", LyricParser.parseSeconds(track.get("lyricTr").getAsString()).size());
            if (track.has("error")) report.addProperty("detail", track.get("error").getAsString());
        } catch (Exception error) {
            report.addProperty("audio", "ERROR:" + error.getClass().getSimpleName());
        }
        return report;
    }

    public static void run(Object sender, String source, String keyword) {
        ZMusic.message.sendNormalMessage("正在检查" + MusicErrorMessages.sourceName(source) + "的搜索、音频与歌词……", sender);
        JsonObject report = inspect(source, keyword);
        ZMusic.message.sendNormalMessage("音乐源：" + MusicErrorMessages.sourceName(source)
                + "；搜索结果：" + (report.has("searchCount") ? report.get("searchCount").getAsInt() : 0) + " 首。", sender);
        if (report.has("title")) ZMusic.message.sendNormalMessage("歌曲：" + report.get("title").getAsString(), sender);
        ZMusic.message.sendNormalMessage("音频：" + MusicErrorMessages.audioStatus(report.get("audio").getAsString()) + "。", sender);
        if (report.has("lyricLines")) ZMusic.message.sendNormalMessage("歌词：原文 "
                + report.get("lyricLines").getAsInt() + " 行；翻译 " + report.get("translatedLines").getAsInt() + " 行。", sender);
        if (report.has("detail") && !report.get("detail").getAsString().isEmpty()) {
            ZMusic.message.sendErrorMessage(MusicErrorMessages.remoteMessage(report.get("detail").getAsString(),
                    "音乐源请求失败，请稍后重试或联系管理员检查账号权限。"), sender);
        }
        try {
            File directory = new File(ZMusic.dataFolder, "diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建诊断目录");
            OtherUtils.saveStringToLocal(new File(directory, source.replaceAll("[^a-zA-Z0-9]", "") + ".json"), report.toString());
        } catch (Exception error) { ZMusic.message.sendErrorMessage("诊断报告保存失败。", sender); }
    }
}
