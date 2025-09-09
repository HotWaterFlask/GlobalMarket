package com.globalmarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MarketManager {
    
    private final GlobalMarket plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, MarketListing> listings;
    private final DatabaseManager databaseManager;
    private final DatabaseStorage databaseStorage;
    private final Mailbox mailbox;
    
    public MarketManager(GlobalMarket plugin) {
        this.plugin = plugin;
        this.listings = new HashMap<>();

        // 初始化数据库管理器
        this.databaseManager = new DatabaseManager(plugin);
        this.databaseStorage = new DatabaseStorage(plugin, databaseManager);
        this.mailbox = new Mailbox(plugin);
        
        // 如果数据库初始化失败，回退到YAML
        if (!databaseManager.initialize()) {
            plugin.getLogger().warning("数据库初始化失败，将使用YAML存储");
        } else if (!databaseManager.isDatabaseEnabled()) {
            plugin.getLogger().info("YAML存储模式已激活");
        }
        
        // 创建数据文件夹和文件（YAML模式使用）
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        this.dataFile = new File(plugin.getDataFolder(), "market_data.yml");
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        if (!databaseManager.isDatabaseEnabled()) {
            plugin.getLogger().info("[YAML模式] YAML存储模式已激活");
        }
        
        loadData();
    }
    
    public void loadData() {
        listings.clear();
        
        if (databaseManager.isDatabaseEnabled()) {
            listings.putAll(databaseStorage.loadListings());
            return;
        }
        
        // YAML模式加载 - 使用新的结构化格式
        if (dataFile.exists()) {
            // **关键修复：重新加载文件内容，确保获取最新数据**
            try {
                dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            } catch (Exception e) {
                plugin.getLogger().warning("配置文件加载失败: " + e.getMessage());
                return;
            }
            
            // 检查是否是旧格式（包含全局标签）
            boolean isOldFormat = false;
            try {
                String rawData = dataConfig.saveToString();
                if (rawData.contains("!!com.globalmarket.MarketListing")) {
                    isOldFormat = true;
                } else {
                    // 新格式：从配置节加载
                    for (String key : dataConfig.getKeys(false)) {
                        try {
                            UUID listingId = UUID.fromString(key);
                            if (dataConfig.isConfigurationSection(key)) {
                                loadListingFromSection(listingId, dataConfig.getConfigurationSection(key));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("市场列表加载失败: " + key);
                        }
                    }
                }
            } catch (Exception e) {
                isOldFormat = true;
            }
            
            // 如果是旧格式，尝试迁移数据
            if (isOldFormat) {
                plugin.getLogger().warning("检测到旧格式数据，正在迁移...");
                migrateOldData();
            }
        }
    }
    
    private void loadListingFromSection(UUID listingId, org.bukkit.configuration.ConfigurationSection section) {
        try {
            String sellerIdStr = section.getString("seller_id");
            String itemBase64 = section.getString("item_base64");
            double price = section.getDouble("price");
            long createdAt = section.getLong("created_at");
            
            if (sellerIdStr != null && itemBase64 != null) {
                UUID sellerId = UUID.fromString(sellerIdStr);
                MarketListing listing = new MarketListing(listingId, sellerId, itemBase64, price, createdAt);
                listings.put(listingId, listing);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("市场列表数据解析失败");
        }
    }
    
    private void migrateOldData() {
        try {
            // 备份旧数据
            File backupFile = new File(plugin.getDataFolder(), "market_data_backup.yml");
            dataConfig.save(backupFile);
            plugin.getLogger().info("数据备份已创建");
            
            // 清空当前数据
            listings.clear();
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            
            plugin.getLogger().info("数据迁移完成");
            
        } catch (Exception e) {
            plugin.getLogger().severe("数据迁移失败: " + e.getMessage());
        }
    }
    
    public void saveData() {
        if (databaseManager.isDatabaseEnabled()) {
            // 数据库模式不需要保存，数据已经实时写入
            return;
        }
        
        try {
                dataConfig = YamlConfiguration.loadConfiguration(dataFile);
                
                for (String key : new ArrayList<>(dataConfig.getKeys(false))) {
                    dataConfig.set(key, null);
                }
                
                for (Map.Entry<UUID, MarketListing> entry : listings.entrySet()) {
                    UUID listingId = entry.getKey();
                    MarketListing listing = entry.getValue();
                    
                    String path = listingId.toString();
                    dataConfig.set(path + ".seller_id", listing.getSellerId().toString());
                    dataConfig.set(path + ".price", listing.getPrice());
                    dataConfig.set(path + ".created_at", listing.getCreatedAt());
                    dataConfig.set(path + ".item_base64", listing.getItemBase64());
                }
                
                dataConfig.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("无法保存市场数据: " + e.getMessage());
            }
    }
    
    public void reload() {
        if (databaseManager.isDatabaseEnabled()) {
            // 数据库模式下重新加载数据
            loadData();
        } else {
            // YAML模式下重新加载
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            loadData();
        }
    }
    
    public void close() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        saveData();
        if (mailbox != null) {
            mailbox.saveMailboxData();
        }
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public boolean isDatabaseEnabled() {
        return databaseManager != null && databaseManager.isDatabaseEnabled();
    }
    
    public UUID createListing(Player seller, ItemStack item, double price) {
        // 验证物品数量
        if (item.getAmount() <= 0) {
            seller.sendMessage(ChatColor.RED + "物品数量必须大于0!");
            return null;
        }
        
        // 验证价格
        if (price <= 0) {
            seller.sendMessage(ChatColor.RED + "价格必须大于0!");
            return null;
        }
        
        // 验证物品是否可以上架
        com.globalmarket.util.ItemValidator.ValidationResult validationResult = 
            com.globalmarket.util.ItemValidator.canListItem(item);
        
        if (!validationResult.isAllowed()) {
            seller.sendMessage(ChatColor.RED + "物品验证失败: " + validationResult.getMessage());
            return null;
        }
        
        UUID listingId = UUID.randomUUID();
        MarketListing listing = new MarketListing(listingId, seller.getUniqueId(), item.clone(), price, System.currentTimeMillis());
        listings.put(listingId, listing);

        // 如果启用数据库，实时保存
        if (databaseManager.isDatabaseEnabled()) {
            databaseStorage.saveListing(listing);
        } else {
            saveData(); // YAML模式保存
        }

        return listingId;
    }
    
    public boolean removeListing(UUID listingId, Player player) {
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            return false;
        }
        
        if (!listing.getSellerId().equals(player.getUniqueId())) {
            return false;
        }
        
        listings.remove(listingId);
        
        if (databaseManager.isDatabaseEnabled()) {
            databaseStorage.removeListing(listingId);
        } else {
            saveData(); // YAML模式保存
        }
        
        return true;
    }
    
    /**
     * 通过Shift+左键下架物品（返回到邮箱）
     */
    public boolean removeListingToMailbox(UUID listingId, Player player) {
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            player.sendMessage(ChatColor.RED + "该物品已不存在或已被下架!");
            return false;
        }
        
        // 检查是否是卖家本人
        if (!listing.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "这不是你上架的物品!");
            return false;
        }
        
        // 立即从内存中移除，防止重复下架
        listings.remove(listingId);
        
        // 同步移除数据库记录 - 强制立即执行
        if (databaseManager.isDatabaseEnabled()) {
            databaseStorage.removeListing(listingId);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Map<UUID, MarketListing> freshListings = databaseStorage.loadListings();
                this.listings.clear();
                this.listings.putAll(freshListings);
            }, 30L);
        } else {
            saveData();
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                loadData();
                
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    loadData();
                }, 10L);
            }, 50L);
        }
        
        // 使用专门的下架方法（添加标记避免交易记录）
        mailbox.addRemovedItemToMailbox(player.getUniqueId(), listing.getItem());
        
        player.sendMessage(ChatColor.GREEN + "物品已下架并存入邮箱!");
        return true;
    }
    
    public MarketListing getListing(UUID listingId) {
        return listings.get(listingId);
    }
    
    public Map<UUID, MarketListing> getAllListings() {
        if (databaseManager.isDatabaseEnabled()) {
            Map<UUID, MarketListing> freshListings = databaseStorage.loadListings();
            this.listings.clear();
            this.listings.putAll(freshListings);
            return new HashMap<>(freshListings);
        }
        
        // 确保数据已加载
        if (listings.isEmpty()) {
            loadData();
        }
        
        // 调试输出
        // 市场数据已加载
        return new HashMap<>(listings);
    }
    
    public boolean purchaseListing(Player buyer, UUID listingId) {
        MarketListing listing = listings.get(listingId);
        if (listing == null) {
            return false;
        }
        
        // 检查买家是否有足够金币
        if (!plugin.getEconomyManager().hasBalance(buyer, listing.getPrice())) {
            return false;
        }
        
        // 扣除买家金币（暂存到系统账户）
        if (!plugin.getEconomyManager().withdraw(buyer, listing.getPrice()).transactionSuccess()) {
            return false;
        }
        
        // 计算税款
        double taxRate = plugin.getConfig().getDouble("transaction-tax", 0) / 100.0;
        double taxAmount = listing.getPrice() * taxRate;
        double sellerAmount = listing.getPrice() - taxAmount;
        
        // 创建交易记录
        Mailbox.TransactionRecord record = new Mailbox.TransactionRecord(
            listing.getItem().getType().name(),
            listing.getItem().getAmount(),
            listing.getPrice(),
            taxAmount,
            sellerAmount
        );
        
        // 获取卖家信息
        OfflinePlayer seller = plugin.getServer().getOfflinePlayer(listing.getSellerId());
        String sellerName = seller.getName();
        String itemName = listing.getItem().getType().name();
        int amount = listing.getItem().getAmount();
        
        // 将资金和交易记录添加到卖家邮箱（卖家不应收到物品，只收到资金）
        mailbox.addItemToMailbox(listing.getSellerId(), null, sellerAmount, record);
        
        // 将购买的物品添加到买家邮箱（买家只收到物品，不收到交易记录）
        mailbox.addItemToMailbox(buyer.getUniqueId(), listing.getItem().clone(), 0, null);
        
        // **关键修复：立即从所有存储中移除商品**
        listings.remove(listingId);
        
        // **强制立即同步移除**
        if (databaseManager.isDatabaseEnabled()) {
            databaseStorage.removeListing(listingId);
            // **强制刷新数据**
            this.listings.clear();
            this.listings.putAll(databaseStorage.loadListings());
        } else {
            saveData();
            // **YAML模式：立即重新加载确保数据同步**
            loadData();
        }
        
        // 发送交易通知
        String itemDisplay = amount + "个" + itemName;
        
        // 获取配置消息
        String buyerMsg = plugin.getConfig().getString("messages.purchase-notification-buyer", 
            "&a[成功] 你购买了 %amount% 个 %item% &a总价: %price% &7物品已存入邮箱!");
        String sellerMsg = plugin.getConfig().getString("messages.purchase-notification-seller", 
            "&6[交易] %buyer% 购买了你上架的 %amount% 个 %item% &6售价: %price% &a实际收入: %net% &7物品已存入邮箱!");
        
        // 格式化消息
        buyerMsg = buyerMsg.replace("%amount%", String.valueOf(amount))
                          .replace("%item%", itemName)
                          .replace("%price%", "$" + listing.getPrice());
        
        // 通知买家
        buyer.sendMessage(ChatColor.translateAlternateColorCodes('&', buyerMsg));
        
        // 通知卖家（如果在线）
        if (seller.isOnline()) {
            Player sellerPlayer = (Player) seller;
            sellerMsg = sellerMsg.replace("%buyer%", buyer.getName())
                               .replace("%amount%", String.valueOf(amount))
                               .replace("%item%", itemName)
                               .replace("%price%", "$" + listing.getPrice())
                               .replace("%net%", "$" + sellerAmount);
            sellerPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', sellerMsg));
        }
        
        // 记录交易日志
        if (plugin.getConfig().getBoolean("log-transactions", true)) {
            plugin.getLogger().info(String.format("交易完成: %s 购买了 %s 的物品 %s 价格: %.2f (税款: %.2f)",
                buyer.getName(),
                sellerName,
                itemName,
                listing.getPrice(),
                taxAmount
            ));
        }
        
        return true;
    }
    
    // 获取玩家上架数量
    public int getPlayerListingCount(UUID playerUUID) {
        if (databaseManager.isDatabaseEnabled()) {
            return databaseStorage.getPlayerListingCount(playerUUID);
        }
        
        int count = 0;
        for (MarketListing listing : listings.values()) {
            if (listing.getSellerId().equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }
    
    public Mailbox getMailbox() {
        return mailbox;
    }
    
    /**
     * 打开物品搜索GUI - 实时显示指定物品的所有上架
     */
    public void openItemSearchGUI(Player player, String itemTypeName) {
        try {
            Material targetMaterial = Material.valueOf(itemTypeName.toUpperCase());
            plugin.getGuiManager().openItemSearchGUI(player, targetMaterial, 0);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "无效的物品类型: " + itemTypeName);
        }
    }
    
    /**
     * 打开玩家搜索GUI - 实时显示指定玩家的所有上架物品
     */
    public void openPlayerSearchGUI(Player player, String targetPlayerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            player.sendMessage(ChatColor.RED + "找不到玩家: " + targetPlayerName);
            return;
        }
        plugin.getGuiManager().openPlayerSearchGUI(player, targetPlayer.getUniqueId(), targetPlayerName, 0);
    }
    
    public void openMailbox(Player player) {
        mailbox.openMailbox(player);
    }
    
    public void openMailbox(Player player, Mailbox.SortType sortType) {
        mailbox.openMailbox(player, sortType);
    }
}