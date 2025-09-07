package com.globalmarket.util;

import com.globalmarket.EconomyManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 离线交易管理器
 * 专门处理离线玩家的游戏币和物品交易
 */
public class OfflineTransactionManager {
    
    private final EconomyManager economyManager;
    private final SimpleOfflineInventory offlineInventory;
    private final Logger logger;
    
    public OfflineTransactionManager(EconomyManager economyManager, SimpleOfflineInventory offlineInventory) {
        this.economyManager = economyManager;
        this.offlineInventory = offlineInventory;
        this.logger = Logger.getLogger("GlobalMarket");
    }
    
    /**
     * 处理离线玩家的上架税收
     * @param playerUUID 玩家UUID
     * @param taxAmount 税收金额
     * @return 是否成功扣除税收
     */
    public boolean processListingTax(@NotNull UUID playerUUID, double taxAmount) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        
        if (offlinePlayer == null) {
            logger.warning("无法找到离线玩家: " + playerUUID);
            return false;
        }
        
        // 检查玩家是否有足够的钱
        if (!economyManager.hasBalance(offlinePlayer, taxAmount)) {
            logger.warning("离线玩家 " + offlinePlayer.getName() + " 余额不足，无法扣除上架税收: " + taxAmount);
            return false;
        }
        
        // 扣除税收
        EconomyResponse response = economyManager.withdraw(offlinePlayer, taxAmount);
        
        if (response.transactionSuccess()) {
            logger.info("成功从离线玩家 " + offlinePlayer.getName() + " 扣除上架费用: " + taxAmount);
            return true;
        } else {
            logger.warning("扣除离线玩家 " + offlinePlayer.getName() + " 上架费用失败: " + response.errorMessage);
            return false;
        }
    }
    
    /**
     * 处理离线玩家的物品扣除
     * @param playerUUID 玩家UUID
     * @param itemType 物品类型
     * @param amount 数量
     * @return 是否成功扣除物品
     */
    public boolean processItemDeduction(@NotNull UUID playerUUID, @NotNull ItemStack itemType, int amount) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        
        if (offlinePlayer == null) {
            logger.warning("无法找到离线玩家: " + playerUUID);
            return false;
        }
        
        // 检查物品是否足够
        if (!offlineInventory.hasEnoughItems(offlinePlayer, itemType, amount)) {
            logger.warning("离线玩家 " + offlinePlayer.getName() + " 物品不足");
            return false;
        }
        
        // 扣除物品
        boolean success = offlineInventory.removeItemsFromPlayer(offlinePlayer, itemType, amount);
        
        if (success) {
            logger.info("成功从离线玩家 " + offlinePlayer.getName() + " 扣除物品: " + 
                       itemType.getType() + " x" + amount);
        } else {
            logger.warning("扣除离线玩家 " + offlinePlayer.getName() + " 物品失败");
        }
        
        return success;
    }
    
    /**
     * 处理完整的离线交易
     * @param playerUUID 玩家UUID
     * @param itemType 物品类型
     * @param itemAmount 物品数量
     * @param taxAmount 税收金额
     * @return 交易结果
     */
    public OfflineTransactionResult processOfflineTransaction(@NotNull UUID playerUUID, 
                                                             @NotNull ItemStack itemType, 
                                                             int itemAmount, 
                                                             double taxAmount) {
        
        OfflineTransactionResult result = new OfflineTransactionResult();
        
        try {
            // 1. 先扣除税收
            boolean taxSuccess = processListingTax(playerUUID, taxAmount);
            if (!taxSuccess) {
                result.setSuccess(false);
                result.setMessage("离线玩家游戏币不足，无法支付上架税收");
                return result;
            }
            
            // 2. 再扣除物品
            boolean itemSuccess = processItemDeduction(playerUUID, itemType, itemAmount);
            if (!itemSuccess) {
                // 如果物品扣除失败，需要返还已扣除的税收
                refundTax(playerUUID, taxAmount);
                result.setSuccess(false);
                result.setMessage("离线玩家物品不足，无法完成上架");
                return result;
            }
            
            // 3. 交易成功
            result.setSuccess(true);
            result.setMessage("离线交易成功完成");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "处理离线交易时发生异常", e);
            result.setSuccess(false);
            result.setMessage("离线交易处理失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 返还税收给离线玩家
     */
    private void refundTax(@NotNull UUID playerUUID, double taxAmount) {
        try {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
            if (offlinePlayer != null) {
                economyManager.deposit(offlinePlayer, taxAmount);
                logger.info("已返还税收给离线玩家 " + offlinePlayer.getName() + ": " + taxAmount);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "返还离线玩家税收失败", e);
        }
    }
    
    /**
     * 获取离线玩家余额
     */
    public double getOfflinePlayerBalance(@NotNull UUID playerUUID) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        if (offlinePlayer != null) {
            return economyManager.getBalance(offlinePlayer);
        }
        return 0.0;
    }
    
    /**
     * 检查离线玩家是否有足够的钱
     */
    public boolean hasOfflinePlayerEnoughMoney(@NotNull UUID playerUUID, double amount) {
        return getOfflinePlayerBalance(playerUUID) >= amount;
    }
    
    /**
     * 检查离线玩家是否有足够的物品
     */
    public boolean hasOfflinePlayerEnoughItems(@NotNull UUID playerUUID, @NotNull ItemStack itemType, int amount) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        if (offlinePlayer == null) {
            return false;
        }
        return offlineInventory.hasEnoughItems(offlinePlayer, itemType, amount);
    }
    
    /**
     * 离线交易结果类
     */
    public static class OfflineTransactionResult {
        private boolean success;
        private String message;
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}