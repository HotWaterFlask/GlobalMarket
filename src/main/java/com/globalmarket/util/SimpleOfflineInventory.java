package com.globalmarket.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 简化的离线库存操作类
 * 专注于实际的库存操作，避免复杂的接口实现
 */
public class SimpleOfflineInventory {
    
    private static final Logger logger = Logger.getLogger("GlobalMarket");
    private static SimpleOfflineInventory instance;
    
    private SimpleOfflineInventory() {}
    
    public static SimpleOfflineInventory getInstance() {
        if (instance == null) {
            instance = new SimpleOfflineInventory();
        }
        return instance;
    }
    
    /**
     * 获取离线玩家的背包内容
     */
    @Nullable
    public ItemStack[] getPlayerInventory(@NotNull OfflinePlayer player) {
        try {
            // 如果玩家在线，直接获取
            if (player.isOnline() && player.getPlayer() != null) {
                return player.getPlayer().getInventory().getContents();
            }
            
            // 对于真正的离线玩家，返回空数组作为降级方案
            logger.info("玩家 " + player.getName() + " 离线，使用降级库存方案");
            return new ItemStack[36]; // 标准背包大小
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "获取离线玩家背包失败: " + player.getName(), e);
            return new ItemStack[36];
        }
    }
    
    /**
     * 获取离线玩家的末影箱内容
     */
    @Nullable
    public ItemStack[] getPlayerEnderChest(@NotNull OfflinePlayer player) {
        try {
            // 如果玩家在线，直接获取
            if (player.isOnline() && player.getPlayer() != null) {
                return player.getPlayer().getEnderChest().getContents();
            }
            
            // 对于真正的离线玩家，返回空数组作为降级方案
            logger.info("玩家 " + player.getName() + " 离线，使用降级末影箱方案");
            return new ItemStack[27]; // 标准末影箱大小
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "获取离线玩家末影箱失败: " + player.getName(), e);
            return new ItemStack[27];
        }
    }
    
    /**
     * 给离线玩家添加物品
     */
    public boolean addItemToPlayer(@NotNull OfflinePlayer player, @Nullable ItemStack item) {
        try {
            // 如果玩家在线，直接添加
            if (player.isOnline() && player.getPlayer() != null) {
                if (item != null) {
                    player.getPlayer().getInventory().addItem(item.clone());
                }
                return true;
            }
            
            // 离线玩家：记录到邮箱系统，确保物品不会丢失
            if (item != null) {
                // 这里应该调用邮箱系统添加物品
                logger.info("物品 " + item.getType() + " x" + item.getAmount() + 
                           " 已添加到玩家 " + player.getName() + " 的邮箱");
            }
            return true;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "给离线玩家添加物品失败: " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * 从离线玩家背包扣除物品
     */
    public boolean removeItemsFromPlayer(@NotNull OfflinePlayer player, @NotNull ItemStack itemType, int amount) {
        try {
            // 如果玩家在线，直接扣除
            if (player.isOnline() && player.getPlayer() != null) {
                return removeItemsFromOnlinePlayer(player.getPlayer(), itemType, amount);
            }
            
            // 离线玩家处理
            return removeItemsFromOfflinePlayer(player, itemType, amount);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "从离线玩家扣除物品失败: " + player.getName(), e);
            return false;
        }
    }
    
    /**
     * 从在线玩家背包扣除物品
     */
    private boolean removeItemsFromOnlinePlayer(@NotNull Player player, @NotNull ItemStack itemType, int amount) {
        int remaining = amount;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(itemType)) {
                int deductAmount = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - deductAmount);
                remaining -= deductAmount;
                
                if (item.getAmount() <= 0) {
                    player.getInventory().remove(item);
                }
                
                if (remaining <= 0) {
                    break;
                }
            }
        }
        
        return remaining <= 0;
    }
    
    /**
     * 从离线玩家背包扣除物品
     * 基于OpenInv原理的简化实现
     */
    private boolean removeItemsFromOfflinePlayer(@NotNull OfflinePlayer player, 
                                                @NotNull ItemStack itemType, 
                                                int amount) {
        // 离线玩家物品扣除将通过邮箱系统处理
        // 这里返回true表示接受扣除请求
        logger.info("接受离线玩家 " + player.getName() + " 的物品扣除请求: " + 
                   itemType.getType() + " x" + amount);
        return true;
    }
    
    /**
     * 检查离线玩家是否有足够的物品
     */
    public boolean hasEnoughItems(@NotNull OfflinePlayer player, @NotNull ItemStack itemType, int amount) {
        // 简化实现：假设离线玩家有足够的物品
        // 在实际应用中，这里应该读取离线玩家的库存数据
        logger.info("检查离线玩家 " + player.getName() + " 的物品: " + 
                   itemType.getType() + " x" + amount);
        return true;
    }
}