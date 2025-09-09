package com.globalmarket.util;

import com.globalmarket.GlobalMarket;
import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ItemValidator {
    
    private static GlobalMarket plugin;
    
    public static void initialize(GlobalMarket plugin) {
        ItemValidator.plugin = plugin;
    }
    
    private static FileConfiguration getConfig() {
        return plugin.getConfig();
    }
    
    // 禁止上架的Lore关键词
    private static final List<String> FORBIDDEN_LORE_KEYWORDS = Arrays.asList(
        "专属", "vip", "VIP", "Binding", "binding", "右键打开", "右键使用", 
        "任务物品", "任务道具", "不可交易", "绑定", "灵魂绑定", "Soulbound",
        "限时", "活动", "礼包", "奖励", "特殊", "管理员", "Admin", "GM",
        "交易记录", "交易凭证", "购买记录", "出售记录"
    );
    
    // 禁止上架的物品类型（可扩展）
    private static final List<String> FORBIDDEN_ITEM_NAMES = Arrays.asList(
        "交易记录", "Transaction Record", "交易凭证"
    );
    
    /**
     * 检查物品是否可以上架到市场
     * @param item 要检查的物品
     * @return ValidationResult 包含是否允许上架及原因
     */
    public static ValidationResult canListItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new ValidationResult(false, "物品不能为空或空气");
        }
        
        FileConfiguration config = getConfig();
        
        // 检查是否启用了物品验证
        if (!config.getBoolean("item-validation.enabled", true)) {
            return new ValidationResult(true, "验证已禁用");
        }
        
        // 检查是否为交易记录
        if (!config.getBoolean("item-validation.allow-transaction-records", false)) {
            if (isTransactionRecord(item)) {
                String message = config.getString("messages.item-validation-transaction-record", "交易记录物品不能上架!");
                return new ValidationResult(false, message);
            }
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 检查物品名称
            if (meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                List<String> bannedNames = config.getStringList("item-validation.banned-item-names");
                if (bannedNames.isEmpty()) {
                    bannedNames = FORBIDDEN_ITEM_NAMES;
                }
                for (String keyword : bannedNames) {
                    if (displayName.toLowerCase().contains(keyword.toLowerCase())) {
                        String message = config.getString("messages.item-validation-banned-name", "物品名称包含禁止关键词: %keyword%")
                                .replace("%keyword%", keyword);
                        return new ValidationResult(false, message);
                    }
                }
            }
            
            // 检查Lore
            if (meta.hasLore()) {
                List<String> lore = meta.getLore();
                List<String> bannedLore = config.getStringList("item-validation.banned-lore-keywords");
                if (bannedLore.isEmpty()) {
                    bannedLore = FORBIDDEN_LORE_KEYWORDS;
                }
                for (String loreLine : lore) {
                    for (String keyword : bannedLore) {
                        if (loreLine.toLowerCase().contains(keyword.toLowerCase())) {
                            String message = config.getString("messages.item-validation-banned-lore", "物品描述包含禁止关键词: %keyword%")
                                    .replace("%keyword%", keyword);
                            return new ValidationResult(false, message);
                        }
                    }
                }
            }
        }
        
        // 检查物品类型
        if (config.getBoolean("item-validation.check-item-types", true)) {
            List<String> bannedTypes = config.getStringList("item-validation.banned-item-types");
            if (bannedTypes.isEmpty()) {
                bannedTypes = Arrays.asList("COMMAND_BLOCK", "STRUCTURE_BLOCK", "BARRIER", "JIGSAW", "LIGHT");
            }
            String itemType = item.getType().name();
            for (String bannedType : bannedTypes) {
                if (itemType.equalsIgnoreCase(bannedType)) {
                    String message = config.getString("messages.item-validation-banned-type", "物品类型禁止上架: %type%")
                            .replace("%type%", bannedType);
                    return new ValidationResult(false, message);
                }
            }
        }
        
        return new ValidationResult(true, "物品验证通过");
    }
    
    /**
     * 检查是否为交易记录物品
     */
    private static boolean isTransactionRecord(ItemStack item) {
        if (item == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        // 快速检查：如果物品类型不是成书，直接返回false
        if (item.getType() != Material.WRITTEN_BOOK) {
            return false;
        }
        
        // 简单检查：物品名称中包含"交易"或"Transaction"
        if (meta.hasDisplayName()) {
            String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            if (displayName.contains("交易") || displayName.contains("transaction")) {
                return true;
            }
        }
        
        // 检查作者是否为GlobalMarket
        if (meta instanceof BookMeta) {
            BookMeta bookMeta = (BookMeta) meta;
            String author = bookMeta.getAuthor();
            if (author != null && (author.equals("GlobalMarket") || author.equals("GlobalMarket系统"))) {
                return true;
            }
        }
        
        // 检查Lore中是否包含交易相关信息
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                String cleanLine = ChatColor.stripColor(line).toLowerCase();
                if (cleanLine.contains("交易时间") || 
                    cleanLine.contains("交易金额") || 
                    cleanLine.contains("实际收入") || 
                    cleanLine.contains("税款") ||
                    cleanLine.contains("出售记录") ||
                    cleanLine.contains("购买记录")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查是否为禁止上架的物品类型
     */
    private static boolean isForbiddenItemType(ItemStack item) {
        // 可以在这里添加特定物品类型的检查
        // 例如：某些特殊物品、命令方块、结构方块等
        
        String materialName = item.getType().name().toUpperCase();
        
        // 禁止命令方块、结构方块等
        List<String> forbiddenTypes = Arrays.asList(
            "COMMAND_BLOCK", "STRUCTURE_BLOCK", "BARRIER", "JIGSAW", "LIGHT"
        );
        
        return forbiddenTypes.contains(materialName);
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean allowed;
        private final String message;
        
        public ValidationResult(boolean allowed, String message) {
            this.allowed = allowed;
            this.message = message;
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getMessage() {
            return message;
        }
    }
}