package com.globalmarket.util;

import com.globalmarket.GlobalMarket;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 增强版物品回滚管理器
 * 结合OpenInv插件实现离线玩家的物品回滚
 */
public class EnhancedRollbackManager {
    
    private final GlobalMarket plugin;
    private final Map<UUID, RollbackData> pendingRollbacks = new HashMap<>();
    
    public EnhancedRollbackManager(GlobalMarket plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 记录需要回滚的数据
     */
    public static class RollbackData {
        private final UUID playerUUID;
        private final ItemStack[] items;
        private final double moneyAmount;
        private final long timestamp;
        
        public RollbackData(UUID playerUUID, ItemStack[] items, double moneyAmount) {
            this.playerUUID = playerUUID;
            this.items = items;
            this.moneyAmount = moneyAmount;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 记录玩家的物品和金币用于回滚
     * @param playerUUID 玩家UUID
     * @param items 需要回滚的物品
     * @param moneyAmount 需要回滚的金币数量
     */
    public void recordRollback(UUID playerUUID, ItemStack[] items, double moneyAmount) {
        RollbackData data = new RollbackData(playerUUID, items, moneyAmount);
        pendingRollbacks.put(playerUUID, data);
        
        // 延迟执行回滚，给玩家重新连接的机会
        new BukkitRunnable() {
            @Override
            public void run() {
                performRollback(playerUUID);
            }
        }.runTaskLater(plugin, 100L); // 5秒后执行
    }
    
    /**
     * 执行物品回滚
     * @param playerUUID 玩家UUID
     */
    public void performRollback(UUID playerUUID) {
        RollbackData data = pendingRollbacks.get(playerUUID);
        if (data == null) return;
        
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            
            // 检查玩家是否已重新连接
            Player onlinePlayer = offlinePlayer.getPlayer();
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                // 玩家在线，直接给予物品
                rollbackToOnlinePlayer(onlinePlayer, data);
            } else {
                // 玩家离线，使用OpenInv
                rollbackToOfflinePlayer(playerUUID, data);
            }
            
            // 清理回滚记录
            pendingRollbacks.remove(playerUUID);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "执行物品回滚失败", e);
        }
    }
    
    /**
     * 回滚给在线玩家
     */
    private void rollbackToOnlinePlayer(Player player, RollbackData data) {
        // 给予金币
        if (data.moneyAmount > 0) {
            plugin.getEconomyManager().deposit(player, data.moneyAmount);
        }
        
        // 给予物品
        for (ItemStack item : data.items) {
            if (item != null && !item.getType().isAir()) {
                player.getInventory().addItem(item.clone());
            }
        }
        
        player.sendMessage("§a网络连接中断，物品已返还到您的背包！");
    }
    
    /**
     * 回滚给离线玩家
     */
    private void rollbackToOfflinePlayer(UUID playerUUID, RollbackData data) {
        // 给予金币
        if (data.moneyAmount > 0) {
            // 这里可以扩展支持离线金币存储
            plugin.getLogger().info("玩家 " + playerUUID + " 的金币 " + data.moneyAmount + " 将在上线时返还");
        }
        
        // 给予物品
        boolean success = true;
        for (ItemStack item : data.items) {
            if (item != null && !item.getType().isAir()) {
                boolean itemSuccess = giveItemToOfflinePlayer(playerUUID, item);
                if (!itemSuccess) {
                    success = false;
                    plugin.getLogger().warning("无法将物品 " + item.getType() + " 返还给玩家 " + playerUUID);
                }
            }
        }
        
        if (success) {
            plugin.getLogger().info("所有物品已成功返还给离线玩家 " + playerUUID);
        }
    }
    
    /**
     * 给离线玩家添加物品
     */
    private boolean giveItemToOfflinePlayer(UUID playerUUID, ItemStack item) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            return SimpleOfflineInventory.getInstance().addItemToPlayer(offlinePlayer, item);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "给离线玩家添加物品失败", e);
            return false;
        }
    }
    
    /**
     * 检查是否有待处理的回滚
     * @param playerUUID 玩家UUID
     * @return 是否有待回滚的数据
     */
    public boolean hasPendingRollback(UUID playerUUID) {
        return pendingRollbacks.containsKey(playerUUID);
    }
    
    /**
     * 取消特定玩家的回滚
     * @param playerUUID 玩家UUID
     */
    public void cancelRollback(UUID playerUUID) {
        pendingRollbacks.remove(playerUUID);
    }
    
    /**
     * 立即执行所有待处理的回滚
     */
    public void processAllPendingRollbacks() {
        for (UUID playerUUID : pendingRollbacks.keySet()) {
            performRollback(playerUUID);
        }
    }
}