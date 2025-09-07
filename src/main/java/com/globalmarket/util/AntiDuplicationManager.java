package com.globalmarket.util;

import com.globalmarket.GlobalMarket;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 反物品复制管理器
 * 防止玩家通过故意离线来绕过物品扣除和税收
 */
public class AntiDuplicationManager {
    
    private final GlobalMarket plugin;
    private final SimpleOfflineInventory offlineInventory;
    private final OfflineTransactionManager offlineTransactionManager;
    private final Logger logger;
    
    public AntiDuplicationManager(GlobalMarket plugin, OfflineTransactionManager offlineTransactionManager) {
        this.plugin = plugin;
        this.offlineInventory = SimpleOfflineInventory.getInstance();
        this.offlineTransactionManager = offlineTransactionManager;
        this.logger = Logger.getLogger("GlobalMarket");
    }
    
    /**
     * 验证玩家物品并安全扣除
     * @param player 玩家
     * @param itemType 物品类型
     * @param amount 数量
     * @param totalPrice 总价（包含税收）
     * @return 验证结果
     */
    public ValidationResult validateAndDeductItems(@NotNull Player player, 
                                                  @NotNull ItemStack itemType, 
                                                  int amount, 
                                                  double totalPrice) {
        
        try {
            // 检查反物品复制机制是否启用
            if (!plugin.getConfig().getBoolean("anti-duplication.enabled", true)) {
                return handleLegacyValidation(player, itemType, amount, totalPrice);
            }
            
            // 根据玩家在线状态选择正确的处理方式
            if (player.isOnline()) {
                return handleOnlineValidation(player, itemType, amount, totalPrice);
            } else {
                return handleOfflineValidation(player, itemType, amount, totalPrice);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "验证和扣除物品时发生异常", e);
            return new ValidationResult(false, "系统错误，请联系管理员");
        }
    }
    
    /**
     * 传统验证方法（兼容模式）
     * @param player 玩家
     * @param itemType 物品类型
     * @param amount 数量
     * @param totalPrice 总价
     * @return 验证结果
     */
    private ValidationResult handleLegacyValidation(@NotNull Player player, 
                                                   @NotNull ItemStack itemType, 
                                                   int amount, 
                                                   double totalPrice) {
        if (player.isOnline()) {
            return handleOnlineValidation(player, itemType, amount, totalPrice);
        } else {
            return handleOfflineValidation(player, itemType, amount, totalPrice);
        }
    }
    
    /**
     * 处理在线玩家的验证
     */
    private ValidationResult handleOnlineValidation(@NotNull Player player, 
                                                     @NotNull ItemStack itemType, 
                                                     int amount, 
                                                     double price) {
        
        // 验证物品数量
        int actualAmount = countItemInInventory(player, itemType);
        if (actualAmount < amount) {
            return new ValidationResult(false, "物品数量不足，需要 " + amount + " 个，实际拥有 " + actualAmount + " 个");
        }
        
        // 注意：这里只验证物品，不扣除游戏币
        // 游戏币扣除由外部单独处理
        
        // 扣除物品
        if (!deductItemsFromInventory(player, itemType, amount)) {
            return new ValidationResult(false, "扣除物品失败");
        }
        
        return new ValidationResult(true, "验证通过");
    }
    
    /**
     * 处理离线玩家的验证
     * 基于OpenInv的离线库存操作原理
     */
    private ValidationResult handleOfflineValidation(@NotNull OfflinePlayer player, 
                                                    @NotNull ItemStack itemType, 
                                                    int amount, 
                                                    double price) {
        
        try {
            // 获取离线玩家库存
            ItemStack[] inventoryContents = offlineInventory.getPlayerInventory(player);
            if (inventoryContents == null) {
                return new ValidationResult(false, "无法获取离线玩家库存");
            }
            
            // 计算离线玩家物品数量
            int actualAmount = countItemInOfflineInventory(inventoryContents, itemType);
            if (actualAmount < amount) {
                return new ValidationResult(false, "离线玩家物品数量不足");
            }
            
            // 注意：这里只验证物品，不扣除游戏币
            // 游戏币扣除由外部单独处理
            
            // 扣除离线玩家物品
            if (!deductItemsFromOfflineInventory(player, inventoryContents, itemType, amount)) {
                return new ValidationResult(false, "扣除离线玩家物品失败");
            }
            
            logger.info("成功扣除离线玩家 " + player.getName() + " 的物品");
            return new ValidationResult(true, "离线验证通过");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "离线玩家验证失败: " + player.getName(), e);
            return new ValidationResult(false, "离线验证异常");
        }
    }
    
    /**
     * 计算玩家背包中指定物品的数量
     */
    private int countItemInInventory(@NotNull Player player, @NotNull ItemStack itemType) {
        int count = 0;
        PlayerInventory inventory = player.getInventory();
        
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.isSimilar(itemType)) {
                count += item.getAmount();
            }
        }
        
        return count;
    }
    
    /**
     * 计算离线玩家库存中指定物品的数量
     */
    private int countItemInOfflineInventory(@NotNull ItemStack[] inventory, @NotNull ItemStack itemType) {
        int count = 0;
        
        for (ItemStack item : inventory) {
            if (item != null && item.isSimilar(itemType)) {
                count += item.getAmount();
            }
        }
        
        return count;
    }
    
    /**
     * 从玩家背包扣除物品
     */
    private boolean deductItemsFromInventory(@NotNull Player player, @NotNull ItemStack itemType, int amount) {
        int remaining = amount;
        PlayerInventory inventory = player.getInventory();
        
        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.isSimilar(itemType)) {
                int deductAmount = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - deductAmount);
                remaining -= deductAmount;
                
                if (item.getAmount() <= 0) {
                    inventory.setItem(i, null);
                }
            }
        }
        
        return remaining <= 0;
    }
    
    /**
     * 从离线玩家库存扣除物品
     * 基于OpenInv的离线库存修改原理
     */
    private boolean deductItemsFromOfflineInventory(@NotNull OfflinePlayer player, 
                                                   @NotNull ItemStack[] inventory, 
                                                   @NotNull ItemStack itemType, 
                                                   int amount) {
        
        int remaining = amount;
        
        for (int i = 0; i < inventory.length && remaining > 0; i++) {
            ItemStack item = inventory[i];
            if (item != null && item.isSimilar(itemType)) {
                int deductAmount = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - deductAmount);
                remaining -= deductAmount;
                
                if (item.getAmount() <= 0) {
                    inventory[i] = null;
                }
            }
        }
        
        // 使用增强的SimpleOfflineInventory方法
        return offlineInventory.removeItemsFromPlayer(player, itemType, amount);
    }
    
    /**
     * 返还物品给在线玩家
     */
    private void giveItemsBack(@NotNull Player player, @NotNull ItemStack itemType, int amount) {
        ItemStack giveBack = itemType.clone();
        giveBack.setAmount(amount);
        player.getInventory().addItem(giveBack);
    }
    
    /**
     * 返还物品给离线玩家
     */
    private void giveItemsBackToOfflinePlayer(@NotNull OfflinePlayer player, 
                                             @NotNull ItemStack itemType, 
                                             int amount) {
        ItemStack giveBack = itemType.clone();
        giveBack.setAmount(amount);
        offlineInventory.addItemToPlayer(player, giveBack);
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean success;
        private final String message;
        
        public ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
    }
}