package me.zhenxin.zmusic.music;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.utils.CookieUtils;
import me.zhenxin.zmusic.utils.log.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
/** 真实网络结果只记录观察值，不把服务商限制伪装成单元测试通过。 */
@EnabledIfSystemProperty(named="zmusic.liveTests", matches="true")
class LiveSourceTest {
    @TempDir Path folder;
    @Test void recordsLiveProviderResults() throws Exception {
        ZMusic.dataFolder = folder.toFile(); ZMusic.thisVer = "2.14.0-CE.2";
        ZMusic.log = new Log() {
            public void sendNormalMessage(String message) { }
            public void sendDebugMessage(String message) { }
            public void sendErrorMessage(String message) { }
            public Object getSender() { return null; }
        };
        CookieUtils.initCookieManager(); Config.neteaseApiRoot = "direct/";
        Path reports = java.nio.file.Paths.get("build", "reports", "live-sources"); Files.createDirectories(reports);
        Config.audioBind = "127.0.0.1"; Config.audioPort = 18089; Config.audioPublicUrl = "http://127.0.0.1:18089";
        me.zhenxin.zmusic.audio.ModAudioServer.start();
        try {
        for (String source : new String[]{"163", "qq", "kugou", "kuwo", "bilibili"}) {
            String keyword = "bilibili".equals(source) ? "BV1GJ411x7h7" : "晴天";
            Files.write(reports.resolve(source + ".json"), SourceDiagnostics.inspect(source, keyword).toString().getBytes(StandardCharsets.UTF_8));
        }
        for (String source : new String[]{"qq", "kugou", "kuwo"}) {
            String keyword = "kuwo".equals(source) ? "起风了" : "Test";
            Files.write(reports.resolve(source + "-alternate.json"), SourceDiagnostics.inspect(source, keyword).toString().getBytes(StandardCharsets.UTF_8));
        }
        com.google.gson.JsonObject raw = com.google.gson.JsonParser.parseString(me.zhenxin.zmusic.utils.WebMusicUtils.get(
                "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=26445261", "https://m.kuwo.cn/yinyue/26445261", null)).getAsJsonObject();
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        result.add("status", raw.get("status"));
        result.addProperty("rows", raw.has("data") && raw.get("data").isJsonObject() && raw.getAsJsonObject("data").get("lrclist").isJsonArray()
                ? raw.getAsJsonObject("data").getAsJsonArray("lrclist").size() : 0);
        Files.write(reports.resolve("kuwo-raw-status.json"), result.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(reports.resolve("kugou-public.json"), SourceDiagnostics.inspect("kugou", "-id:1bc927f73529ea92ce6fc50a34febeca,172378798,8830386150").toString().getBytes(StandardCharsets.UTF_8));
        Files.write(reports.resolve("kuwo-public.json"), SourceDiagnostics.inspect("kuwo", "-id:51685512").toString().getBytes(StandardCharsets.UTF_8));
        } finally { me.zhenxin.zmusic.audio.ModAudioServer.close(); }
    }
}
