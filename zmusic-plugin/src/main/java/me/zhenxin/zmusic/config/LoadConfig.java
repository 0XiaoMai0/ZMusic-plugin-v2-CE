package me.zhenxin.zmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.utils.NetUtils;
import me.zhenxin.zmusic.utils.OtherUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class LoadConfig {

    public void load() {
        File oldConfig = new File(ZMusic.dataFolder.getPath(), "config.yml");
        if (oldConfig.exists()) {
            File reToOld = new File(ZMusic.dataFolder.getPath(), "config.yml.old");
            if (!oldConfig.renameTo(reToOld)) {
                reToOld.delete();
                oldConfig.renameTo(reToOld);
            }
        }
        File config = new File(ZMusic.dataFolder.getPath(), "config.json");
        if (!config.exists()) {
            ZMusic.log.sendErrorMessage("无法找到配置文件,正在创建!");
            saveDefaultConfig();
        }
        String json = OtherUtils.readFileToString(config);
        JsonObject configJson;
        try {
            configJson = new Gson().fromJson(json, JsonObject.class);
        } catch (Exception e) {
            ZMusic.log.sendErrorMessage("配置文件出错,正在重置!");
            File configErrBak = new File(ZMusic.dataFolder.getPath(), System.currentTimeMillis() + "_error-config.json");
            config.renameTo(configErrBak);
            config.delete();
            saveDefaultConfig();
            load();
            return;
        }
        Config.version = configJson.get("version").getAsInt();
        if (Config.version != Config.latestVersion) {
            ZMusic.log.sendNormalMessage("-- 正在更新配置文件...");
            config = new File(ZMusic.dataFolder.getPath(), "config.json");
            File configBak = new File(ZMusic.dataFolder.getPath(), "config.json.v" + Config.version + ".bak");
            ZMusic.log.sendNormalMessage("-- 正在备份原配置文件...");
            config.renameTo(configBak);
            ZMusic.log.sendNormalMessage("-- 正在释放新配置文件...");
            saveDefaultConfig();
            ZMusic.log.sendNormalMessage("-- 更新完毕.");
            load();
            return;
        }
        migrateAudioConfig(configJson, config);
        disableOfficialUpdate(configJson, config);
        init(configJson);
    }

    private void disableOfficialUpdate(JsonObject json, File file) {
        if (json.has("check-update") && json.get("check-update").isJsonPrimitive()
                && json.getAsJsonPrimitive("check-update").isBoolean()
                && !json.get("check-update").getAsBoolean()) return;
        json.addProperty("check-update", false);
        try {
            OtherUtils.saveStringToLocal(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            ZMusic.log.sendNormalMessage("社区版已关闭旧配置中的官方更新检查。");
        } catch (IOException error) {
            ZMusic.log.sendErrorMessage("无法保存更新检查设置；社区版仍不会请求官方更新接口，请检查 config.json 写入权限。");
        }
    }

    private void migrateAudioConfig(JsonObject json, File file) {
        if (json.has("audio-stream") && !json.has("web-player")) return;
        if (!json.has("audio-stream")) {
            JsonObject audio = new JsonObject();
            JsonObject old = json.has("web-player") ? json.getAsJsonObject("web-player") : new JsonObject();
            audio.addProperty("enabled", !old.has("enabled") || old.get("enabled").getAsBoolean());
            audio.addProperty("bind", old.has("bind") ? old.get("bind").getAsString() : "0.0.0.0");
            audio.addProperty("port", old.has("port") ? old.get("port").getAsInt() : 18081);
            audio.addProperty("public-url", old.has("public-url") ? old.get("public-url").getAsString() : "http://127.0.0.1:18081");
            json.add("audio-stream", audio);
        }
        json.remove("web-player");
        try {
            File backup = new File(file.getPath() + ".before-ce2.bak");
            if (!backup.exists()) Files.copy(file.toPath(), backup.toPath());
            OtherUtils.saveStringToLocal(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            ZMusic.log.sendNormalMessage("已添加游戏模组音频配置 audio-stream；公网服请设置 public-url 为玩家可访问地址。");
        } catch (IOException error) {
            ZMusic.log.sendErrorMessage("无法保存 audio-stream 配置，请检查 config.json 写入权限。");
        }
    }

    private void init(JsonObject config) {
        // Version
        Config.version = config.get("version").getAsInt();
        // Debug
        Config.debug = config.get("debug").getAsBoolean();
        // 兼容旧配置字段；社区版不使用官方更新服务。
        Config.checkUpdate = false;
        // Prefix
        Config.prefix = config.get("prefix").getAsString().replaceAll("&", "§");
        // Api
        JsonObject api = config.get("api").getAsJsonObject();
        String neteaseApiRoot = api.get("netease").getAsString();
        if (!neteaseApiRoot.endsWith("/")) {
            neteaseApiRoot += "/";
        }
        Config.neteaseApiRoot = neteaseApiRoot;
        // NeteaseFollow
        Config.neteaseFollow = config.get("netease-follow").getAsBoolean();
        // VIP
        JsonObject vip = config.get("vip").getAsJsonObject();
        Config.vipAccount = vip.get("account").getAsString();
        Config.vipSecret = vip.get("secret").getAsString();
        // CE 不依赖作者 VIP 转码服务；平台账号权益仍由各音乐平台验证。
        if (config.has("audio-stream")) {
            JsonObject audio = config.getAsJsonObject("audio-stream");
            Config.audioEnabled = !audio.has("enabled") || audio.get("enabled").getAsBoolean();
            Config.audioBind = audio.has("bind") ? audio.get("bind").getAsString() : "0.0.0.0";
            Config.audioPort = audio.has("port") ? audio.get("port").getAsInt() : 18081;
            Config.audioPublicUrl = audio.has("public-url") ? audio.get("public-url").getAsString() : "http://127.0.0.1:18081";
        }
        // Music
        JsonObject music = config.get("music").getAsJsonObject();
        Config.money = music.get("money").getAsInt();
        Config.cooldown = music.get("cooldown").getAsInt();
        // Lyric
        JsonObject lyric = config.get("lyric").getAsJsonObject();
        Config.lyricEnable = lyric.get("enable").getAsBoolean();
        Config.showLyricTr = lyric.get("showLyricTr").getAsBoolean();
        Config.lyricColor = lyric.get("color").getAsString().replaceAll("&", "§");
        if (Config.realSupportBossBar) {
            Config.supportBossBar = lyric.get("bossBar").getAsBoolean();
        }
        if (Config.realSupportActionBar) {
            Config.supportActionBar = lyric.get("actionBar").getAsBoolean();
        }
        if (Config.realSupportTitle) {
            Config.supportTitle = lyric.get("subTitle").getAsBoolean();
        }
        Config.supportChat = lyric.get("chatMessage").getAsBoolean();
        if (Config.realSupportHud) {
            JsonObject hud = lyric.get("hud").getAsJsonObject();
            Config.supportHud = hud.get("enable").getAsBoolean();
            Config.hudInfoX = hud.get("infoX").getAsInt();
            Config.hudInfoY = hud.get("infoY").getAsInt();
            Config.hudLyricX = hud.get("lyricX").getAsInt();
            Config.hudLyricY = hud.get("lyricY").getAsInt();
        }
        // 1.8 无 BossBar 和 Hud；默认配置也应能看到歌词。
        if (Config.lyricEnable && !Config.supportBossBar && !Config.supportHud
                && !Config.supportActionBar && !Config.supportTitle && !Config.supportChat) {
            Config.supportActionBar = Config.realSupportActionBar;
            Config.supportChat = !Config.supportActionBar;
        }

    }

    private void saveDefaultConfig() {
        File config = new File(ZMusic.dataFolder.getPath(), "config.json");
        try {
            Files.copy(this.getClass().getResourceAsStream("/config.json"), config.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload(Object sender) {
        load();
        ZMusic.message.sendNormalMessage("配置文件重载完毕!", sender);
    }
}
