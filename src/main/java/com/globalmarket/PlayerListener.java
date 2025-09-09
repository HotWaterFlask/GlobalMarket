package com.globalmarket;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

/**
 * 玩家事件监听器
 */
public class PlayerListener implements Listener {
    
    private final GlobalMarket plugin;
    
    public PlayerListener(GlobalMarket plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // 清理玩家相关的GUI数据
        plugin.getGuiManager().cleanupPlayer(player.getUniqueId());
    }
}