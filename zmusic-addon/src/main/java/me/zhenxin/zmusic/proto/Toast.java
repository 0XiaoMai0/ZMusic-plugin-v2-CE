package me.zhenxin.zmusic.proto;
import me.zhenxin.zmusic.ZMusicAddon;
import org.bukkit.entity.Player;
/** 代理服 Addon 使用公开 Title API，不依赖服务端内部类。 */
public final class Toast {
    public static void sendToast(Object player, String title) {
        Player target = (Player) player;
        ZMusicAddon.plugin.getServer().getScheduler().runTask(ZMusicAddon.plugin, () -> {
            String[] lines = title.split("\n", 2);
            try {
                target.sendTitle(lines[0], lines.length > 1 ? lines[1] : "", 0, 60, 10);
            } catch (NoSuchMethodError ignored) {
                target.sendTitle(lines[0], lines.length > 1 ? lines[1] : "");
            }
        });
    }
}
