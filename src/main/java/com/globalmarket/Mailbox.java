package com.globalmarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class Mailbox {
    
    private final GlobalMarket plugin;
    private final Map<UUID, List<MailboxItem>> playerMailboxes = new HashMap<>();
    
    public Mailbox(GlobalMarket plugin) {
        this.plugin = plugin;
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
        List<MailboxItem> items = getMailboxItems(player.getUniqueId());
        
        if (items.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "你的邮箱是空的!");
            return;
        }
        
        int size = Math.min(54, ((items.size() - 1) / 9 + 1) * 9);
        Inventory mailbox = Bukkit.createInventory(null, size, ChatColor.GOLD + "玩家邮箱");
        
        for (MailboxItem item : items) {
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                
                if (item.isRemovedItem()) {
                    // 下架物品的特殊显示
                    lore.add(ChatColor.RED + "[已下架] " + ChatColor.GRAY + "从市场下架的物品");
                    lore.add(ChatColor.YELLOW + "点击取回背包");
                } else if (item.isSentItem()) {
                    // 邮寄物品的特殊显示
                    lore.add(ChatColor.LIGHT_PURPLE + "[邮寄] " + ChatColor.GRAY + "来自: " + ChatColor.GOLD + item.getSenderName());
                    lore.add(ChatColor.YELLOW + "点击取回背包");
                } else {
                    // 正常物品显示
                    if (item.getMoney() > 0) {
                        lore.add(ChatColor.GREEN + "价值: " + plugin.getEconomyManager().formatCurrency(item.getMoney()));
                    }
                    lore.add(ChatColor.YELLOW + "点击领取");
                }
                
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            mailbox.addItem(display);
        }
        
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
        
        // 创建交易记录成书
        ItemStack recordBook = createTransactionRecordBook(mailboxItem.getRecord());
        if (recordBook == null) {
            return false;
        }
        
        // 添加到玩家背包
        player.getInventory().addItem(recordBook);
        player.getInventory().addItem(mailboxItem.getItem());
        
        // 汇入资金
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
        
        player.sendMessage(ChatColor.GREEN + "物品和交易记录已领取!");
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