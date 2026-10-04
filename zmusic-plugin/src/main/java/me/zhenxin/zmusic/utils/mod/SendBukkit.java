package me.zhenxin.zmusic.utils.mod;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicBukkit;
import me.zhenxin.zmusic.api.Version;
import me.zhenxin.zmusic.utils.runtask.BukkitTaskScheduler;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

public class SendBukkit implements Send {

    private final Version version = new Version();

    @Override
    public void sendAM(Object playerObj, String data) {
        Player player = (Player) playerObj;
        if (player == null)
            return;
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] array = new byte[bytes.length + 1];
            array[0] = (byte) 666;
            System.arraycopy(bytes, 0, array, 1, bytes.length);
            // 同一玩家队列内发送两个频道，保证 [Stop] 和 [Play] 的顺序。
            BukkitTaskScheduler.run(player, () -> {
                player.sendPluginMessage(ZMusicBukkit.plugin, "allmusic:channel", array);
                player.sendPluginMessage(ZMusicBukkit.plugin, "zmusic:channel", array);
            });
        } catch (Exception e) {
            ZMusic.log.sendDebugMessage("[Mod通信] 数据发送发生错误");
        }
    }

    @Override
    public void sendABF(Object playerObj, String data) {
        if (!version.isHigherThan("1.12")) {
            Player player = (Player) playerObj;
            if (player == null)
                return;
            try {
                BukkitTaskScheduler.run(player, () -> player.sendPluginMessage(ZMusicBukkit.plugin,
                        "AudioBuffer", data.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                ZMusic.log.sendDebugMessage("[Mod通信] 数据发送发生错误");
            }
        }
    }

    @Override
    public void sendToZMusicAddon(Object playerObj, String data) {
        // Bukkit 平台不需要实现 ZMusic Addon 通信
        // ZMusic Addon 是 BungeeCord 专用功能
        // Bukkit 可以通过其他方式传递数据，这里留空实现
    }
}
