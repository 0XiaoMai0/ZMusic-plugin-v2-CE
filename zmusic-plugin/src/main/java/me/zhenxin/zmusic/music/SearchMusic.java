package me.zhenxin.zmusic.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.component.ZClickEvent;
import me.zhenxin.zmusic.component.ZComponent;
import me.zhenxin.zmusic.component.ZHoverEvent;
import me.zhenxin.zmusic.component.ZTextComponent;
import me.zhenxin.zmusic.config.Config;
import me.zhenxin.zmusic.language.Lang;
import me.zhenxin.zmusic.music.searchSource.BiliBiliMusic;
import me.zhenxin.zmusic.music.searchSource.KuwoMusic;
import me.zhenxin.zmusic.music.searchSource.QQMusic;
import me.zhenxin.zmusic.music.searchSource.KugouMusic;
import me.zhenxin.zmusic.music.searchSource.NeteaseCloudMusic;

public class SearchMusic {

    public static void sendList(String searchKey, String source, Object player) {
        source = source.toLowerCase(java.util.Locale.ROOT);
        String musicID, musicName, musicSinger, musicFullName, searchSourceName;
        JsonArray json;
        ZMusic.message.sendNormalMessage("正在搜索中...", player);
        switch (source) {
            case "163":
            case "netease":
                json = NeteaseCloudMusic.getMusicList(searchKey);
                searchSourceName = "网易云音乐";
                break;
            case "kuwo":
                json = KuwoMusic.getMusicList(searchKey);
                searchSourceName = "酷我音乐";
                break;
            case "qq":
                json = QQMusic.getMusicList(searchKey);
                searchSourceName = "QQ音乐";
                break;
            case "kugou":
                json = KugouMusic.getMusicList(searchKey);
                searchSourceName = "酷狗音乐";
                break;
            case "bilibili":
                json = BiliBiliMusic.getMusicList(searchKey);
                searchSourceName = "哔哩哔哩视频";
                break;
            default:
                ZMusic.message.sendErrorMessage("错误：未知的搜索源", player);
                return;
        }
        if (json != null && json.size() > 0) {
            me.zhenxin.zmusic.music.searchSource.SongMetadataCache.remember(source, json);
            ZMusic.message.sendNormalMessage("§6=========================================", player);
            int i = 1;
            for (JsonElement j : json) {

                musicName = j.getAsJsonObject().get("name").getAsString();
                musicSinger = j.getAsJsonObject().get("singer").getAsString();
                musicFullName = musicName + " - " + musicSinger;
                ZComponent message = ZTextComponent.of(Config.prefix + "§a" + i + "." + musicFullName);
                i++;
                ZComponent play = ZTextComponent.of("§r[§e" + Lang.clickPlay + "§r]§r");
                ZComponent music = ZTextComponent.of("§r[§e" + Lang.clickMusic + "§r]§r");
                if (source.equalsIgnoreCase("163") ||
                    source.equalsIgnoreCase("netease") ||
                    source.equalsIgnoreCase("qq") || source.equalsIgnoreCase("kugou") || source.equalsIgnoreCase("kuwo") ||
                    source.equalsIgnoreCase("bilibili")) {
                    musicID = j.getAsJsonObject().get("id").getAsString();
                    play.setClickEvent(ZClickEvent.runCommand("/zm play " + source + " -id:" + musicID));
                    music.setClickEvent(ZClickEvent.runCommand("/zm music " + source + " -id:" + musicID));
                } else {
                    play.setClickEvent(ZClickEvent.runCommand("/zm play " + source + " " + musicName));
                    music.setClickEvent(ZClickEvent.runCommand("/zm music " + source + " " + musicName));
                }
                play.setHoverEvent(ZHoverEvent.showText("§b" + Lang.clickPlayText));
                music.setHoverEvent(ZHoverEvent.showText("§b" + Lang.clickMusicText));
                message.addChild(ZTextComponent.of(" "));
                message.addChild(play);
                message.addChild(ZTextComponent.of(" "));
                message.addChild(music);
                ZMusic.message.sendJsonMessage(message, player);
            }
            ZMusic.message.sendNormalMessage("§6=========================================", player);
        } else {
            ZMusic.message.sendPlayError(player, searchKey);
        }
    }
}
