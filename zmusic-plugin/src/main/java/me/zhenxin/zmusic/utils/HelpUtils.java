package me.zhenxin.zmusic.utils;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.language.Lang;
import java.util.Locale;

public class HelpUtils {

    /**
     * 发送帮助
     *
     * @param type      帮助类型
     * @param playerObj 玩家
     */
    public static void sendHelp(String type, Object playerObj) {
        boolean isAdmin;
        if (ZMusic.player.isPlayer(playerObj))
            isAdmin = ZMusic.player.hasPermission(playerObj, "zmusic.admin");
        else isAdmin = true;
        boolean canPlayAll = isAdmin || ZMusic.player.hasPermission(playerObj, "zmusic.playall");
        switch (type.toLowerCase(Locale.ROOT)) {
            case "main":
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d帮助 作者：真心 §6=========", playerObj);
                for (String s : Lang.mainHelp) {
                    if (s.contains("[admin]")) {
                        if (isAdmin) ZMusic.message.sendNormalMessage(s.split("\\[admin]")[1], playerObj);
                    } else {
                        ZMusic.message.sendNormalMessage(s, playerObj);
                    }
                }
                break;
            case "admin":
                if (!isAdmin) {
                    ZMusic.message.sendErrorMessage("需要 zmusic.admin 权限。", playerObj);
                    return;
                }
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d管理员帮助 作者：真心 §6=========", playerObj);
                ZMusic.message.sendNormalMessage("/zm playAll [搜索源] [歌名] - 强制为所有玩家播放音乐.", playerObj);
                ZMusic.message.sendNormalMessage("/zm stopAll - 强制为所有玩家停止播放音乐.", playerObj);
                ZMusic.message.sendNormalMessage("/zm update - 查看社区版更新说明（官方检查已关闭）.", playerObj);
                ZMusic.message.sendNormalMessage("/zm login <平台> raw <Cookie> - 保存平台登录信息；长 Cookie 可写入配置目录的对应文件。", playerObj);
                ZMusic.message.sendNormalMessage("/zm login <平台> status - 查看登录信息保存状态。", playerObj);
                ZMusic.message.sendNormalMessage("/zm login qq qr [qq|wechat] - QQ或微信扫码登录 QQ音乐。", playerObj);
                ZMusic.message.sendNormalMessage("/zm login qq sendcode <手机号> - QQ音乐短信登录；再使用 verify 提交验证码。", playerObj);
                ZMusic.message.sendNormalMessage("/zm login kuwo captcha - 获取酷我图形验证码，再选择 password 或 sendcode 登录。", playerObj);
                ZMusic.message.sendNormalMessage("/zm diagnose <平台> <歌名> - 检查搜索、音频和歌词。", playerObj);
                ZMusic.message.sendNormalMessage("/zm notice read <公告编号> - 标记公告已读。", playerObj);
                ZMusic.message.sendNormalMessage("/zm reload - 重载配置文件.", playerObj);
                ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                break;
            case "play":
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d播放帮助 作者：真心 §6=========", playerObj);
                ZMusic.message.sendNormalMessage("/zm play 163 <歌名> - 网易云音乐播放§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm play qq <歌名> - QQ音乐播放§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm play kugou <歌名> - 酷狗音乐播放§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm play kuwo <歌名> - 酷我音乐播放§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm play bilibili <BV号/关键词> - 哔哩哔哩视频音频播放§a.", playerObj);
                ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                break;
            case "playlist":
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d歌单帮助 作者：真心 §6=========", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist [163/netease] import <歌单链接> - 导入歌单§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist [163/netease] list - 查看已导入的歌单列表§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist [163/netease] play <歌单ID> - 播放已导入的歌单§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist [163/netease] show <歌单ID> - 查看已导入的歌单§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist [163/netease] update - 更新已导入歌单.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist type [normal/loop/random] - 设置歌单播放模式§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist prev - 切换到上一首歌曲§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist next - 切换到下一首歌曲§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist jump <曲目序号> [当前歌单ID] - 跳转到指定歌曲§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] list - 查看全服歌单列表§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] show <歌单ID> [起始序号] - 查看全服歌单§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] play <歌单ID> - 为自己播放全服歌单§a.", playerObj);
                if (canPlayAll) ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] playall <歌单ID> - 为所有玩家播放全服歌单§a.", playerObj);
                if (isAdmin) {
                    ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                    ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] import <歌单链接> - 导入全服歌单§a.", playerObj);
                    ZMusic.message.sendNormalMessage("/zm playlist global [163/netease] update - 更新已导入歌单.", playerObj);
                }
                ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                break;
            case "music":
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d点歌帮助 作者：真心 §6=========", playerObj);
                ZMusic.message.sendNormalMessage("/zm music 163 <歌名> - 网易云音乐点歌§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm music qq <歌名> - QQ音乐点歌§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm music kugou <歌名> - 酷狗音乐点歌§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm music kuwo <歌名> - 酷我音乐点歌§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm music bilibili <BV号/关键词> - 哔哩哔哩视频音频点歌§a.", playerObj);
                ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                break;
            case "search":
                ZMusic.message.sendNormalMessage("§6========= §r[§bZMusic§r] §d搜索帮助 作者：真心 §6=========", playerObj);
                ZMusic.message.sendNormalMessage("/zm search 163 <歌名> - 网易云音乐搜索§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm search qq <歌名> - QQ音乐搜索§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm search kugou <歌名> - 酷狗音乐搜索§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm search kuwo <歌名> - 酷我音乐搜索§a.", playerObj);
                ZMusic.message.sendNormalMessage("/zm search bilibili <关键词> - 哔哩哔哩视频搜索§a.", playerObj);
                ZMusic.message.sendNormalMessage("§6=========================================", playerObj);
                break;
            case "url":
                ZMusic.message.sendNormalMessage("/zm url <音乐直链> - 播放链接的音乐§a.", playerObj);
                break;
            default:
                ZMusic.message.sendNull(playerObj);
        }
    }
}
