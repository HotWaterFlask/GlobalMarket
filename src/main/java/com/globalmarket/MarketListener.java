package com.globalmarket;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.List;

public class MarketListener implements Listener {
    
    private final GlobalMarket plugin;
    
    public MarketListener(GlobalMarket plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 延迟1秒后检查邮箱，确保玩家完全登录
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkMailboxOnJoin(player);
        }, 20L); // 20 ticks = 1秒
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 玩家退出时的逻辑，可以在这里保存玩家数据
    }
    
    /**
     * 检查玩家邮箱并在有内容时发送提示
     */
    private void checkMailboxOnJoin(Player player) {
        List<Mailbox.MailboxItem> items = plugin.getMarketManager().getMailbox().getMailboxItems(player.getUniqueId());
        
        if (items.isEmpty()) {
            return;
        }
        
        // 计算不同类型物品的数量
        int totalItems = items.size();
        int removedItems = 0;
        int sentItems = 0;
        int tradeItems = 0;
        int unclaimedItems = 0;
        
        for (Mailbox.MailboxItem item : items) {
            if (item.isRemovedItem()) {
                removedItems++;
            } else if (item.isSentItem()) {
                sentItems++;
            } else if (item.getMoney() > 0) {
                tradeItems++;
            } else {
                unclaimedItems++;
            }
        }
        
        // 获取配置消息
        String summaryMsg = plugin.getConfig().getString("messages.mailbox-login-summary", 
            "&b[邮箱] 您有新的邮件！");
        String detailMsg = plugin.getConfig().getString("messages.mailbox-login-detail", 
            "&7  - %count% 个 %type%");
        String commandMsg = plugin.getConfig().getString("messages.mailbox-login-command", 
            "&7使用 /market mail 查看邮箱详情");
        
        // 发送统一的邮箱提示
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', summaryMsg));
        
        // 详细提示
        if (removedItems > 0) {
            String msg = detailMsg.replace("%count%", String.valueOf(removedItems))
                                 .replace("%type%", "手动或自动下架的商品");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        if (tradeItems > 0) {
            String msg = detailMsg.replace("%count%", String.valueOf(tradeItems))
                                 .replace("%type%", "交易记录（含资金）");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        if (sentItems > 0) {
            String msg = detailMsg.replace("%count%", String.valueOf(sentItems))
                                 .replace("%type%", "来自别人的邮寄物品");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        if (unclaimedItems > 0) {
            String msg = detailMsg.replace("%count%", String.valueOf(unclaimedItems))
                                 .replace("%type%", "未签收的物品");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', commandMsg));
    }
}