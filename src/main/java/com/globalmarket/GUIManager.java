package com.globalmarket;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GUIManager {
    
    private final GlobalMarket plugin;
    private final String MARKET_GUI_TITLE = ChatColor.GOLD + "全球市场";
    private final String CONFIRM_GUI_TITLE = ChatColor.RED + "确认购买";
    private final String SAFE_GUI_TITLE = ChatColor.GREEN + "安全交易";
    
    // 排序类型枚举
    public enum SortType {
        NEWEST("&b最新优先", Comparator.comparingLong((MarketListing l) -> l.getCreatedAt()).reversed()),
        OLDEST("&e最早优先", Comparator.comparingLong(MarketListing::getCreatedAt)),
        PRICE_LOW("&a价格升序", Comparator.comparingDouble(MarketListing::getPrice)),
        PRICE_HIGH("&c价格降序", Comparator.comparingDouble((MarketListing l) -> l.getPrice()).reversed());
        
        private final String displayName;
        private final Comparator<MarketListing> comparator;
        
        SortType(String displayName, Comparator<MarketListing> comparator) {
            this.displayName = ChatColor.translateAlternateColorCodes('&', displayName);
            this.comparator = comparator;
        }
        
        public String getDisplayName() { return displayName; }
        public Comparator<MarketListing> getComparator() { return comparator; }
        
        public SortType next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }
    
    // 玩家排序状态存储
    private final Map<UUID, SortType> playerSortType = new HashMap<>();
    
    public GUIManager(GlobalMarket plugin) {
        this.plugin = plugin;
    }
    
    public SortType getPlayerSortType(Player player) {
        return playerSortType.getOrDefault(player.getUniqueId(), SortType.NEWEST);
    }
    
    public void setPlayerSortType(Player player, SortType sortType) {
        playerSortType.put(player.getUniqueId(), sortType);
    }
    
    public void openMarketGUI(Player player, int page) {
        // 获取玩家当前的排序方式，默认为最新优先
        SortType currentSort = playerSortType.getOrDefault(player.getUniqueId(), SortType.NEWEST);
        
        Inventory gui = Bukkit.createInventory(null, 54, MARKET_GUI_TITLE + " - 第" + (page + 1) + "页");
        
        // 强制刷新并获取最新市场数据
        Map<UUID, MarketListing> allListings = plugin.getMarketManager().getAllListings();
        List<Map.Entry<UUID, MarketListing>> sortedListings = new ArrayList<>(allListings.entrySet());
        sortedListings.sort((e1, e2) -> currentSort.getComparator().compare(e1.getValue(), e2.getValue()));
        
        // 计算分页
        int startIndex = page * 45; // 每页45个物品
        int totalItems = sortedListings.size();
        
        // 如果没有任何物品，不显示任何物品，只保留导航按钮
        if (totalItems == 0) {
            // 空市场时不显示任何物品，只保留导航按钮
            // 导航按钮会在下方addNavigationButtons方法中自动添加
        } else {
            // 填充当前页的物品
            for (int i = startIndex; i < Math.min(startIndex + 45, totalItems); i++) {
                Map.Entry<UUID, MarketListing> entry = sortedListings.get(i);
                UUID listingId = entry.getKey();
                MarketListing listing = entry.getValue();
                ItemStack item = listing.getItem().clone();
                
                // 添加物品信息
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    boolean isOwnItem = listing.getSellerId().equals(player.getUniqueId());
                    
                    // 保持原始物品名称，让客户端自动翻译
                    String originalDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                    if (originalDisplayName != null && !originalDisplayName.isEmpty()) {
                        // 如果物品已有自定义名称，保留它
                        meta.setDisplayName(ChatColor.GOLD + originalDisplayName);
                    } else {
                        // 使用物品类型名称，让客户端本地化翻译
                        // 不设置displayName，保持默认物品名称
                    }
                    
                    List<String> lore = new ArrayList<>(Arrays.asList(
                        ChatColor.WHITE + "数量: " + item.getAmount(),
                        ChatColor.GREEN + "价格: $" + listing.getPrice(),
                        ChatColor.AQUA + "卖家: " + Bukkit.getOfflinePlayer(listing.getSellerId()).getName(),
                        ChatColor.GRAY + "上架时间: " + formatTime(listing.getCreatedAt())
                    ));
                    
                    if (isOwnItem) {
                        lore.add("");
                        lore.add(ChatColor.RED + "这是你上架的物品");
                        lore.add(ChatColor.YELLOW + "Shift+左键点击下架到邮箱");
                    } else {
                        lore.add("");
                        lore.add(ChatColor.GREEN + "双击购买");
                    }
                    
                    lore.add(ChatColor.GRAY + "ID: " + listingId.toString().substring(0, 8));
                    
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                
                gui.addItem(item);
            }
        }
        
        // 添加导航和排序按钮
        addNavigationButtons(gui, page, totalItems, currentSort);
        
        player.openInventory(gui);
    }
    

    
    public void openSellGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.GOLD + "出售物品");
        
        // 出售槽位
        ItemStack sellSlot = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta slotMeta = sellSlot.getItemMeta();
        if (slotMeta != null) {
            slotMeta.setDisplayName(ChatColor.YELLOW + "将物品放在这里出售");
            sellSlot.setItemMeta(slotMeta);
        }
        gui.setItem(13, sellSlot);
        
        // 价格设置
        ItemStack priceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceMeta = priceItem.getItemMeta();
        if (priceMeta != null) {
            priceMeta.setDisplayName(ChatColor.GREEN + "设置价格");
            priceMeta.setLore(Arrays.asList(
                ChatColor.WHITE + "左键: +1",
                ChatColor.WHITE + "右键: +10",
                ChatColor.WHITE + "Shift+左键: +100",
                ChatColor.WHITE + "Shift+右键: 重置"
            ));
            priceItem.setItemMeta(priceMeta);
        }
        gui.setItem(15, priceItem);
        
        // 确认出售
        ItemStack confirmItem = new ItemStack(Material.GREEN_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName(ChatColor.GREEN + "确认出售");
            confirmItem.setItemMeta(confirmMeta);
        }
        gui.setItem(16, confirmItem);
        
        player.openInventory(gui);
    }
    
    private void addNavigationButtons(Inventory gui, int currentPage, int totalItems, SortType currentSort) {
        int totalPages = (int) Math.ceil(totalItems / 45.0);
        
        // 上一页
        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.YELLOW + "上一页");
                prevPage.setItemMeta(prevMeta);
            }
            gui.setItem(48, prevPage);
        }
        
        // 下一页
        if (currentPage < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + "下一页");
                nextPage.setItemMeta(nextMeta);
            }
            gui.setItem(50, nextPage);
        }
        
        // 关闭按钮
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "关闭");
            close.setItemMeta(closeMeta);
        }
        gui.setItem(49, close);
        
        // 排序按钮
        ItemStack sortButton = new ItemStack(Material.COMPARATOR);
        ItemMeta sortMeta = sortButton.getItemMeta();
        if (sortMeta != null) {
            sortMeta.setDisplayName(ChatColor.GOLD + "排序方式");
            sortMeta.setLore(Arrays.asList(
                ChatColor.WHITE + "当前: " + currentSort.getDisplayName(),
                ChatColor.YELLOW + "点击切换排序方式",
                ChatColor.GRAY + "循环: 最新→最早→低价→高价"
            ));
            sortButton.setItemMeta(sortMeta);
        }
        gui.setItem(53, sortButton);
    }
    
    public void openItemSearchGUI(Player player, Material targetMaterial, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, 
            ChatColor.GOLD + "搜索: " + targetMaterial.name() + " - 第" + (page + 1) + "页");
        
        // 获取玩家当前的排序方式
        SortType currentSort = playerSortType.getOrDefault(player.getUniqueId(), SortType.NEWEST);
        
        // 实时获取最新市场数据
        Map<UUID, MarketListing> allListings = plugin.getMarketManager().getAllListings();
        
        // 筛选指定物品类型的上架
        List<Map.Entry<UUID, MarketListing>> filteredListings = new ArrayList<>();
        for (Map.Entry<UUID, MarketListing> entry : allListings.entrySet()) {
            if (entry.getValue().getItem().getType() == targetMaterial) {
                filteredListings.add(entry);
            }
        }
        
        // 排序筛选后的结果
        filteredListings.sort((e1, e2) -> currentSort.getComparator().compare(e1.getValue(), e2.getValue()));
        
        int totalItems = filteredListings.size();
        int startIndex = page * 45; // 每页45个物品
        
        if (filteredListings.isEmpty()) {
            // 空搜索结果时不显示任何物品，只保留导航按钮
            // 导航按钮会在下方addNavigationButtons方法中自动添加
        } else {
            // 显示当前页的搜索结果
            for (int i = startIndex; i < Math.min(startIndex + 45, totalItems); i++) {
                Map.Entry<UUID, MarketListing> entry = filteredListings.get(i);
                UUID listingId = entry.getKey();
                MarketListing listing = entry.getValue();
                ItemStack item = listing.getItem().clone();
                
                // 添加物品信息
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    boolean isOwnItem = listing.getSellerId().equals(player.getUniqueId());
                    
                    // 保持原始物品名称，让客户端自动翻译
                    String originalDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                    if (originalDisplayName != null && !originalDisplayName.isEmpty()) {
                        // 如果物品已有自定义名称，保留它
                        meta.setDisplayName(ChatColor.GOLD + originalDisplayName);
                    } else {
                        // 使用物品类型名称，让客户端本地化翻译
                        // 不设置displayName，保持默认物品名称
                    }
                    
                    List<String> lore = new ArrayList<>(Arrays.asList(
                        ChatColor.WHITE + "数量: " + item.getAmount(),
                        ChatColor.GREEN + "价格: $" + listing.getPrice(),
                        ChatColor.AQUA + "卖家: " + Bukkit.getOfflinePlayer(listing.getSellerId()).getName(),
                        ChatColor.GRAY + "上架时间: " + formatTime(listing.getCreatedAt())
                    ));
                    
                    if (isOwnItem) {
                        lore.add("");
                        lore.add(ChatColor.RED + "这是你上架的物品");
                        lore.add(ChatColor.YELLOW + "Shift+左键点击下架到邮箱");
                    } else {
                        lore.add("");
                        lore.add(ChatColor.GREEN + "双击购买");
                    }
                    
                    lore.add(ChatColor.GRAY + "ID: " + listingId.toString().substring(0, 8));
                    
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                
                gui.setItem(i - startIndex, item);
            }
        }
        
        // 添加导航和排序按钮
        addNavigationButtons(gui, page, totalItems, currentSort);
        
        player.openInventory(gui);
    }
    
    public void openPlayerSearchGUI(Player player, UUID targetPlayerUUID, String targetPlayerName, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, 
            ChatColor.GOLD + "玩家: " + targetPlayerName + " 的上架物品 - 第" + (page + 1) + "页");
        
        // 获取玩家当前的排序方式
        SortType currentSort = playerSortType.getOrDefault(player.getUniqueId(), SortType.NEWEST);
        
        // 实时获取最新市场数据
        Map<UUID, MarketListing> allListings = plugin.getMarketManager().getAllListings();
        
        // 筛选指定玩家的上架
        List<Map.Entry<UUID, MarketListing>> filteredListings = new ArrayList<>();
        for (Map.Entry<UUID, MarketListing> entry : allListings.entrySet()) {
            if (entry.getValue().getSellerId().equals(targetPlayerUUID)) {
                filteredListings.add(entry);
            }
        }
        
        // 排序筛选后的结果
        filteredListings.sort((e1, e2) -> currentSort.getComparator().compare(e1.getValue(), e2.getValue()));
        
        int totalItems = filteredListings.size();
        int startIndex = page * 45; // 每页45个物品
        
        if (filteredListings.isEmpty()) {
            // 空玩家搜索结果时不显示任何物品，只保留导航按钮
            // 导航按钮会在下方addNavigationButtons方法中自动添加
        } else {
            // 显示当前页的玩家上架物品
            for (int i = startIndex; i < Math.min(startIndex + 45, totalItems); i++) {
                Map.Entry<UUID, MarketListing> entry = filteredListings.get(i);
                UUID listingId = entry.getKey();
                MarketListing listing = entry.getValue();
                ItemStack item = listing.getItem().clone();
                
                // 添加物品信息
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    boolean isOwnItem = listing.getSellerId().equals(player.getUniqueId());
                    
                    // 保持原始物品名称，让客户端自动翻译
                    String originalDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                    if (originalDisplayName != null && !originalDisplayName.isEmpty()) {
                        // 如果物品已有自定义名称，保留它
                        meta.setDisplayName(ChatColor.GOLD + originalDisplayName);
                    } else {
                        // 使用物品类型名称，让客户端本地化翻译
                        // 不设置displayName，保持默认物品名称
                    }
                    
                    List<String> lore = new ArrayList<>(Arrays.asList(
                        ChatColor.WHITE + "数量: " + item.getAmount(),
                        ChatColor.GREEN + "价格: $" + listing.getPrice(),
                        ChatColor.AQUA + "卖家: " + targetPlayerName,
                        ChatColor.GRAY + "上架时间: " + formatTime(listing.getCreatedAt())
                    ));
                    
                    if (isOwnItem) {
                        lore.add("");
                        lore.add(ChatColor.RED + "这是你上架的物品");
                        lore.add(ChatColor.YELLOW + "Shift+左键点击下架到邮箱");
                    } else {
                        lore.add("");
                        lore.add(ChatColor.GREEN + "双击购买");
                    }
                    
                    lore.add(ChatColor.GRAY + "ID: " + listingId.toString().substring(0, 8));
                    
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                
                gui.setItem(i - startIndex, item);
            }
        }
        
        // 添加导航和排序按钮
        addNavigationButtons(gui, page, totalItems, currentSort);
        
        player.openInventory(gui);
    }
    
    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        long hours = diff / (1000 * 60 * 60);
        long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
        
        if (hours > 0) {
            return hours + "小时" + minutes + "分钟前";
        } else {
            return minutes + "分钟前";
        }
    }
    
    /**
     * 清理玩家相关的GUI数据
     */
    public void cleanupPlayer(UUID playerUUID) {
        playerSortType.remove(playerUUID);
    }
}