package com.globalmarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.util.*;

public class Mailbox {
    
    private final GlobalMarket plugin;
    private final Map<UUID, List<MailboxItem>> playerMailboxes = new HashMap<>();
    private final File mailboxDataFile;
    
    public Mailbox(GlobalMarket plugin) {
        this.plugin = plugin;
        this.mailboxDataFile = new File(plugin.getDataFolder(), "mailbox.yml");
        loadMailboxData();
    }
    
    /**
     * 添加物品到玩家邮箱
     */
    public void addItemToMailbox(UUID playerId, ItemStack item, double money, TransactionRecord record) {
        playerMailboxes.computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(new MailboxItem(item, money, record));
        
        // 发送邮箱通知
        String message = plugin.getConfig().getString("messages.mailbox-notification-new-item", 
            "&b[邮箱] 有新的物品到达邮箱! &7使用 /market mail 查看详情");
        notifyPlayer(playerId, message);
        
        // 自动保存邮箱数据
        saveMailboxData();
    }

    /**
     * 添加下架物品到玩家邮箱（特殊标记）
     */
    public void addRemovedItemToMailbox(UUID playerId, ItemStack item) {
        // 创建下架记录
        TransactionRecord record = new TransactionRecord(
            item.getType().name(),
            item.getAmount(),
            0, // 下架不产生收入
            0, // 无税款
            0  // 无实际收入
        );

        // 标记为下架物品
        MailboxItem mailboxItem = new MailboxItem(item, 0, record);
        mailboxItem.setRemovedItem(true);

        playerMailboxes.computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(mailboxItem);
        
        // 发送下架通知
        String message = plugin.getConfig().getString("messages.mailbox-notification-removed", 
            "&e[下架] 你的物品已从市场下架并返回邮箱! &7使用 /market mail 查看详情");
        notifyPlayer(playerId, message);
        
        // 自动保存邮箱数据
        saveMailboxData();
    }

    /**
     * 添加邮寄物品到玩家邮箱
     */
    public void addSentItemToMailbox(UUID playerId, ItemStack item, String senderName) {
        // 创建邮寄记录（无交易金额）
        TransactionRecord record = new TransactionRecord(
            item.getType().name(),
            item.getAmount(),
            0, // 邮寄无售价
            0, // 无税款
            0  // 无实际收入
        );

        // 创建邮箱物品并标记为邮寄物品
        MailboxItem mailboxItem = new MailboxItem(item, 0, record);
        mailboxItem.setSentItem(true);
        mailboxItem.setSenderName(senderName);

        playerMailboxes.computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(mailboxItem);
        
        // 发送邮寄通知
        String message = plugin.getConfig().getString("messages.mailbox-notification-sent", 
            "&d[邮寄] %sender% 给你邮寄了物品! &7使用 /market mail 查看详情");
        message = message.replace("%sender%", senderName);
        notifyPlayer(playerId, message);
    }

    /**
     * 通知玩家有新邮件
     */
    private void notifyPlayer(UUID playerId, String message) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            String commandMsg = plugin.getConfig().getString("messages.mailbox-login-command", 
                "&7使用 /market mail 查看邮箱详情");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', commandMsg));
        }
    }
    
    /**
     * 获取玩家邮箱内容
     */
    public List<MailboxItem> getMailboxItems(UUID playerId) {
        return playerMailboxes.getOrDefault(playerId, new ArrayList<>());
    }
    
    /**
     * 检查玩家是否有未领取的邮件
     */
    public boolean hasMail(UUID playerId) {
        return !getMailboxItems(playerId).isEmpty();
    }
    
    /**
     * 打开玩家邮箱界面
     */
    public void openMailbox(Player player) {
        openMailbox(player, SortType.NEWEST);
    }
    
    /**
     * 打开玩家邮箱界面（带排序）
     */
    public void openMailbox(Player player, SortType sortType) {
        List<MailboxItem> items = getMailboxItems(player.getUniqueId());
        
        // 固定显示5行内容 + 1行导航 = 6行 = 54格
        int size = 54;
        Inventory mailbox = Bukkit.createInventory(null, size, ChatColor.GOLD + "玩家邮箱");
        
        if (items.isEmpty()) {
            // 空邮箱也打开GUI，显示提示信息
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = emptyItem.getItemMeta();
            if (emptyMeta != null) {
                emptyMeta.setDisplayName(ChatColor.RED + "邮箱是空的");
                emptyMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "你的邮箱目前没有物品",
                    ChatColor.YELLOW + "出售物品或接收邮寄物品后会出现在这里"
                ));
                emptyItem.setItemMeta(emptyMeta);
            }
            mailbox.setItem(22, emptyItem);
        } else {
            // 按时间排序
            items.sort((item1, item2) -> {
                if (sortType == SortType.NEWEST) {
                    return Long.compare(item2.getRecord() != null ? item2.getRecord().getTimestamp() : 0,
                                      item1.getRecord() != null ? item1.getRecord().getTimestamp() : 0);
                } else {
                    return Long.compare(item1.getRecord() != null ? item1.getRecord().getTimestamp() : 0,
                                      item2.getRecord() != null ? item2.getRecord().getTimestamp() : 0);
                }
            });
            
            // 显示物品（最多45个）
            for (int i = 0; i < Math.min(items.size(), 45); i++) {
                MailboxItem item = items.get(i);
                ItemStack display = item.getItem().clone();
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    List<String> lore = new ArrayList<>();
                    
                    if (item.isRemovedItem()) {
                        lore.add(ChatColor.RED + "[已下架] " + ChatColor.GRAY + "从市场下架的物品");
                    } else if (item.isSentItem()) {
                        lore.add(ChatColor.LIGHT_PURPLE + "[邮寄] " + ChatColor.GRAY + "来自: " + ChatColor.GOLD + item.getSenderName());
                    } else {
                        if (item.getMoney() > 0) {
                            lore.add(ChatColor.GREEN + "价值: " + plugin.getEconomyManager().formatCurrency(item.getMoney()));
                        }
                    }
                    
                    // 添加时间信息
                    long timestamp = item.getRecord() != null ? item.getRecord().getTimestamp() : System.currentTimeMillis();
                    lore.add(ChatColor.GRAY + "时间: " + new Date(timestamp).toLocaleString());
                    lore.add(ChatColor.YELLOW + "点击取回背包");
                    
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
                mailbox.setItem(i, display);
            }
        }
        
        // 添加排序按钮
        addMailboxNavigationButtons(mailbox, sortType, items.isEmpty());
        
        player.openInventory(mailbox);
    }
    
    /**
     * 领取邮箱中的物品和资金
     */
    public boolean claimMailboxItem(Player player, int index) {
        UUID playerId = player.getUniqueId();
        List<MailboxItem> items = getMailboxItems(playerId);
        
        if (index < 0 || index >= items.size()) {
            return false;
        }
        
        MailboxItem mailboxItem = items.get(index);
        
        // 检查背包空间
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "你的背包已满! 请先清理背包空间。");
            return false;
        }
        
        // 只为实际交易生成交易记录，下架物品不生成
        if (!mailboxItem.isRemovedItem() && mailboxItem.getRecord() != null && mailboxItem.getRecord().getActualIncome() > 0) {
            // 创建交易记录成书
            ItemStack recordBook = createTransactionRecordBook(mailboxItem.getRecord());
            if (recordBook != null) {
                player.getInventory().addItem(recordBook);
            }
        }
        
        // 添加到玩家背包
        player.getInventory().addItem(mailboxItem.getItem());
        
        // 汇入资金（下架物品没有资金）
        if (mailboxItem.getMoney() > 0) {
            plugin.getEconomyManager().deposit(player, mailboxItem.getMoney());
            player.sendMessage(ChatColor.GREEN + "收到资金: " + 
                plugin.getEconomyManager().formatCurrency(mailboxItem.getMoney()));
        }
        
        // 从邮箱移除
        items.remove(index);
        if (items.isEmpty()) {
            playerMailboxes.remove(playerId);
        }
        
        if (mailboxItem.isRemovedItem()) {
            player.sendMessage(ChatColor.GREEN + "下架物品已取回!");
        } else {
            player.sendMessage(ChatColor.GREEN + "物品和交易记录已领取!");
        }
        return true;
    }
    
    /**
     * 创建交易记录成书
     */
    private ItemStack createTransactionRecordBook(TransactionRecord record) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        
        if (bookMeta == null) return null;
        
        bookMeta.setTitle(ChatColor.GOLD + "交易记录");
        bookMeta.setAuthor("GlobalMarket系统");
        
        StringBuilder content = new StringBuilder();
        content.append("交易记录:\n\n");
        content.append("出售物品:").append(record.getItemName()).append("\n");
        content.append("出售数量:").append(record.getAmount()).append("\n");
        content.append("出售价格:").append(plugin.getEconomyManager().formatCurrency(record.getSellPrice())).append("\n");
        content.append("出售税款:").append(plugin.getEconomyManager().formatCurrency(record.getTax())).append("\n");
        content.append("实际收入:").append(plugin.getEconomyManager().formatCurrency(record.getActualIncome())).append("\n\n");
        content.append("交易时间:").append(new Date(record.getTimestamp())).append("\n");
        
        bookMeta.setPages(content.toString());
        book.setItemMeta(bookMeta);
        
        return book;
    }
    
    /**
     * 邮箱物品类
     */
    public static class MailboxItem {
        private final ItemStack item;
        private final double money;
        private final TransactionRecord record;
        private boolean isRemovedItem = false;
        private boolean isSentItem = false;
        private String senderName = null;
        
        public MailboxItem(ItemStack item, double money, TransactionRecord record) {
            this.item = item;
            this.money = money;
            this.record = record;
        }
        
        public ItemStack getItem() { return item; }
        public double getMoney() { return money; }
        public TransactionRecord getRecord() { return record; }
        public boolean isRemovedItem() { return isRemovedItem; }
        public void setRemovedItem(boolean removed) { this.isRemovedItem = removed; }
        public boolean isSentItem() { return isSentItem; }
        public void setSentItem(boolean sent) { this.isSentItem = sent; }
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
    }
    
    /**
     * 邮箱排序类型
     */
    public enum SortType {
        NEWEST("最新优先", "按时间从新到旧排序"),
        OLDEST("最早优先", "按时间从旧到新排序");
        
        private final String displayName;
        private final String description;
        
        SortType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
        
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        
        public SortType next() {
            return this == NEWEST ? OLDEST : NEWEST;
        }
    }
    
    /**
     * 添加邮箱导航按钮
     */
    private void addMailboxNavigationButtons(Inventory gui, SortType currentSort, boolean isEmpty) {
        // 排序按钮
        ItemStack sortButton = new ItemStack(Material.COMPARATOR);
        ItemMeta sortMeta = sortButton.getItemMeta();
        if (sortMeta != null) {
            sortMeta.setDisplayName(ChatColor.GOLD + "排序方式");
            sortMeta.setLore(Arrays.asList(
                ChatColor.WHITE + "当前: " + currentSort.getDisplayName(),
                ChatColor.YELLOW + "点击切换排序方式",
                ChatColor.GRAY + currentSort.getDescription()
            ));
            sortButton.setItemMeta(sortMeta);
        }
        gui.setItem(53, sortButton);
        
        // 关闭按钮
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "关闭");
            closeMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "点击关闭邮箱"
            ));
            closeButton.setItemMeta(closeMeta);
        }
        gui.setItem(49, closeButton);
        
        // 如果邮箱为空，不显示其他按钮
        if (isEmpty) {
            return;
        }
        
        // 刷新按钮
        ItemStack refreshButton = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName(ChatColor.GREEN + "刷新");
            refreshMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "点击刷新邮箱内容"
            ));
            refreshButton.setItemMeta(refreshMeta);
        }
        gui.setItem(45, refreshButton);
    }
    
    /**
     * 保存邮箱数据到文件
     */
    public void saveMailboxData() {
        try {
            FileConfiguration config = new YamlConfiguration();
            
            for (Map.Entry<UUID, List<MailboxItem>> entry : playerMailboxes.entrySet()) {
                UUID playerId = entry.getKey();
                List<MailboxItem> items = entry.getValue();
                
                if (!items.isEmpty()) {
                    String path = playerId.toString() + ".items";
                    
                    for (int i = 0; i < items.size(); i++) {
                        MailboxItem item = items.get(i);
                        String itemPath = path + "." + i;
                        
                        // 保存物品
                        config.set(itemPath + ".item", serializeItemStack(item.getItem()));
                        config.set(itemPath + ".money", item.getMoney());
                        config.set(itemPath + ".isRemoved", item.isRemovedItem());
                        config.set(itemPath + ".isSent", item.isSentItem());
                        config.set(itemPath + ".sender", item.getSenderName());
                        
                        // 保存交易记录
                        TransactionRecord record = item.getRecord();
                        if (record != null) {
                            config.set(itemPath + ".record.itemName", record.getItemName());
                            config.set(itemPath + ".record.amount", record.getAmount());
                            config.set(itemPath + ".record.sellPrice", record.getSellPrice());
                            config.set(itemPath + ".record.tax", record.getTax());
                            config.set(itemPath + ".record.actualIncome", record.getActualIncome());
                            config.set(itemPath + ".record.timestamp", record.getTimestamp());
                        }
                    }
                }
            }
            
            config.save(mailboxDataFile);
            plugin.getLogger().info("邮箱数据已保存到文件");
            
        } catch (Exception e) {
            plugin.getLogger().severe("保存邮箱数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从文件加载邮箱数据
     */
    public void loadMailboxData() {
        try {
            if (!mailboxDataFile.exists()) {
                return;
            }
            
            FileConfiguration config = YamlConfiguration.loadConfiguration(mailboxDataFile);
            
            for (String playerIdStr : config.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerIdStr);
                    String path = playerIdStr + ".items";
                    
                    if (config.contains(path)) {
                        List<MailboxItem> items = new ArrayList<>();
                        
                        for (String itemKey : config.getConfigurationSection(path).getKeys(false)) {
                            String itemPath = path + "." + itemKey;
                            
                            // 读取物品
                            String itemBase64 = config.getString(itemPath + ".item");
                            ItemStack item = deserializeItemStack(itemBase64);
                            
                            if (item != null) {
                                double money = config.getDouble(itemPath + ".money");
                                boolean isRemoved = config.getBoolean(itemPath + ".isRemoved");
                                boolean isSent = config.getBoolean(itemPath + ".isSent");
                                String sender = config.getString(itemPath + ".sender");
                                
                                // 读取交易记录
                                TransactionRecord record = null;
                                if (config.contains(itemPath + ".record")) {
                                    String itemName = config.getString(itemPath + ".record.itemName");
                                    int amount = config.getInt(itemPath + ".record.amount");
                                    double sellPrice = config.getDouble(itemPath + ".record.sellPrice");
                                    double tax = config.getDouble(itemPath + ".record.tax");
                                    double actualIncome = config.getDouble(itemPath + ".record.actualIncome");
                                    long timestamp = config.getLong(itemPath + ".record.timestamp");
                                    
                                    record = new TransactionRecord(itemName, amount, sellPrice, tax, actualIncome);
                                    // 设置时间戳（通过反射或构造函数）
                                    try {
                                        java.lang.reflect.Field timestampField = TransactionRecord.class.getDeclaredField("timestamp");
                                        timestampField.setAccessible(true);
                                        timestampField.setLong(record, timestamp);
                                    } catch (Exception e) {
                                        // 如果反射失败，使用当前时间
                                    }
                                }
                                
                                MailboxItem mailboxItem = new MailboxItem(item, money, record);
                                mailboxItem.setRemovedItem(isRemoved);
                                mailboxItem.setSentItem(isSent);
                                mailboxItem.setSenderName(sender);
                                
                                items.add(mailboxItem);
                            }
                        }
                        
                        if (!items.isEmpty()) {
                            playerMailboxes.put(playerId, items);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("加载玩家邮箱数据失败: " + playerIdStr + " - " + e.getMessage());
                }
            }
            
            plugin.getLogger().info("邮箱数据已从文件加载");
            
        } catch (Exception e) {
            plugin.getLogger().severe("加载邮箱数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 序列化物品为Base64字符串
     */
    private String serializeItemStack(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("序列化物品失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 反序列化Base64字符串为物品
     */
    private ItemStack deserializeItemStack(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            plugin.getLogger().severe("反序列化物品失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 交易记录类
     */
    public static class TransactionRecord {
        private final String itemName;
        private final int amount;
        private final double sellPrice;
        private final double tax;
        private final double actualIncome;
        private final long timestamp;
        
        public TransactionRecord(String itemName, int amount, double sellPrice, double tax, double actualIncome) {
            this.itemName = itemName;
            this.amount = amount;
            this.sellPrice = sellPrice;
            this.tax = tax;
            this.actualIncome = actualIncome;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getItemName() { return itemName; }
        public int getAmount() { return amount; }
        public double getSellPrice() { return sellPrice; }
        public double getTax() { return tax; }
        public double getActualIncome() { return actualIncome; }
        public long getTimestamp() { return timestamp; }
    }
}