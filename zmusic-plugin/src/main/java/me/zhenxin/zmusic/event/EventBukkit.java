package me.zhenxin.zmusic.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventBukkit implements Listener {

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Event.onJoin(event.getPlayer());
    }
    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
    }
}
