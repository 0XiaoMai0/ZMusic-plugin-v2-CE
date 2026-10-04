package me.zhenxin.zmusic.proto;
import me.zhenxin.zmusic.ZMusic;
/** 使用公开 Title API 提示播放信息，避免跨版本进度包的链接错误。 */
public final class Toast {
    public static void sendToast(Object player, String title) {
        String[] lines = title.split("\n", 2);
        ZMusic.message.sendTitleMessage(lines[0], lines.length > 1 ? lines[1] : "", player);
    }
}
