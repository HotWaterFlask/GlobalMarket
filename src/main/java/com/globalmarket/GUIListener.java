package com.globalmarket;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIListener implements Listener {
    
    private final GlobalMarket plugin;
    private final GUIManager guiManager;
    private final Map<UUID, UUID> pendingPurchases;
    
    public GUIListener(GlobalMarket plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.pendingPurchases = new HashMap<>();
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();
        
        if (title.startsWith("§6全球市场")) {
            handleMarketGUIClick(event, player);
        } else if (title.startsWith("§6搜索:")) {
            handleSearchGUIClick(event, player);
        } else if (title.startsWith("§6玩家:")) {
            handleSearchGUIClick(event, player);
        } else if (title.equals("§c确认购买")) {
            handleConfirmGUIClick(event, player);
        } else if (title.equals("§6出售物品")) {
            handleSellGUIClick(event, player);
        }
    }
    
    private void handleMarketGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        String title = event.getView().getTitle();
        
        // 处理排序按钮
        if (clickedItem.getType() == Material.COMPARATOR) {
            // 切换到下一个排序方式
            GUIManager.SortType currentSort = guiManager.getPlayerSortType(player);
            GUIManager.SortType nextSort = currentSort.next();
            guiManager.setPlayerSortType(player, nextSort);
            
            // 重新打开GUI应用新排序
            int currentPage = extractPageNumber(title);
            player.closeInventory();
            guiManager.openMarketGUI(player, 0); // 回到第一页
            return;
        }
        
        // 处理翻页按钮
        if (clickedItem.getType() == Material.ARROW) {
            if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals("§e上一页")) {
                int currentPage = extractPageNumber(title);
                if (currentPage > 0) {
                    player.closeInventory();
                    guiManager.openMarketGUI(player, currentPage - 1);
                }
            } else if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals("§e下一页")) {
                int currentPage = extractPageNumber(title);
                player.closeInventory();
                guiManager.openMarketGUI(player, currentPage + 1);
            }
        }
        
        // 处理关闭按钮
        if (clickedItem.getType() == Material.BARRIER && 
            clickedItem.getItemMeta() != null && 
            clickedItem.getItemMeta().getDisplayName().equals("§c关闭")) {
            player.closeInventory();
            return;
        }
        
        // 处理物品购买或下架
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            for (String lore : clickedItem.getItemMeta().getLore()) {
                if (lore.contains("ID: ")) {
                    String idString = lore.substring(lore.lastIndexOf(" ") + 1);
                    try {
                        UUID listingId = UUID.fromString(idString);
                        MarketListing listing = plugin.getMarketManager().getListing(listingId);
                        if (listing == null) {
                            player.sendMessage(ChatColor.RED + "该物品已不存在!");
                            return;
                        }

                        // 检查是否是卖家自己的物品
                        if (listing.getSellerId().equals(player.getUniqueId())) {
                            // Shift+左键下架到邮箱
                            if (event.isShiftClick() && event.isLeftClick()) {
                                player.closeInventory();
                                plugin.getMarketManager().removeListingToMailbox(listingId, player);
                                // 重新打开GUI显示更新后的列表
                                guiManager.openMarketGUI(player, extractPageNumber(title));
                                return;
                            } else {
                                // 普通点击提示
                                player.sendMessage(ChatColor.YELLOW + "Shift+左键点击可下架此物品到邮箱!");
                                return;
                            }
                        }

                        // 普通购买流程
                        pendingPurchases.put(player.getUniqueId(), listingId);
                        player.closeInventory();
                        guiManager.openConfirmGUI(player, listingId);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "无效的物品ID!");
                    }
                    break;
                }
            }
        }
    }
    
    private void handleConfirmGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        if (clickedItem.getType() == Material.GREEN_WOOL) {
            // 确认购买
            UUID listingId = pendingPurchases.get(player.getUniqueId());
            if (listingId != null) {
                processPurchase(player, listingId);
            }
            player.closeInventory();
            pendingPurchases.remove(player.getUniqueId());
        } else if (clickedItem.getType() == Material.RED_WOOL) {
            // 取消购买
            player.closeInventory();
            pendingPurchases.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "已取消购买");
        }
    }
    
    private void handleSellGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;
        
        // 这里可以扩展出售GUI的功能
        // 目前主要用于展示，实际出售仍通过命令
    }
    
    private void processPurchase(Player player, UUID listingId) {
        MarketListing listing = plugin.getMarketManager().getListing(listingId);
        if (listing == null) {
            player.sendMessage(ChatColor.RED + "该物品已不存在");
            return;
        }
        
        // 检查买家是否有足够金币
        if (!plugin.getEconomyManager().hasBalance(player, listing.getPrice())) {
            player.sendMessage(ChatColor.RED + "你没有足够的金币! 需要: " + 
                plugin.getEconomyManager().formatCurrency(listing.getPrice()));
            return;
        }
        
        // 处理交易（物品和资金将进入邮箱）
        boolean success = plugin.getMarketManager().purchaseListing(player, listingId);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "购买成功! 物品已发送到您的邮箱");
            player.sendMessage(ChatColor.YELLOW + "使用 /market mailbox 查看邮箱");
        } else {
            player.sendMessage(ChatColor.RED + "购买失败!");
        }
    }
    
    private void handleSearchGUIClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        
        String title = event.getView().getTitle();
        
        // 处理排序按钮
        if (clickedItem.getType() == Material.COMPARATOR) {
            // 切换到下一个排序方式
            GUIManager.SortType currentSort = guiManager.getPlayerSortType(player);
            GUIManager.SortType nextSort = currentSort.next();
            guiManager.setPlayerSortType(player, nextSort);
            
            // 重新打开当前搜索GUI应用新排序
            int currentPage = extractPageNumber(title);
            player.closeInventory();
            
            // 根据GUI类型重新打开
            if (title.startsWith("§6搜索:")) {
                String materialName = extractSearchMaterial(title);
                try {
                    Material targetMaterial = Material.valueOf(materialName.toUpperCase());
                    guiManager.openItemSearchGUI(player, targetMaterial, 0);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "搜索参数错误");
                }
            } else if (title.startsWith("§6玩家:")) {
                String playerName = extractSearchPlayer(title);
                @SuppressWarnings("deprecation")
                OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(playerName);
                if (targetPlayer != null && targetPlayer.hasPlayedBefore()) {
                    guiManager.openPlayerSearchGUI(player, targetPlayer.getUniqueId(), playerName, 0);
                } else {
                    player.sendMessage(ChatColor.RED + "找不到玩家");
                }
            }
            return;
        }
        
        // 处理翻页按钮
        if (clickedItem.getType() == Material.ARROW) {
            if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals("§e上一页")) {
                int currentPage = extractPageNumber(title);
                if (currentPage > 0) {
                    player.closeInventory();
                    
                    if (title.startsWith("§6搜索:")) {
                        String materialName = extractSearchMaterial(title);
                        try {
                            Material targetMaterial = Material.valueOf(materialName.toUpperCase());
                            guiManager.openItemSearchGUI(player, targetMaterial, currentPage - 1);
                        } catch (IllegalArgumentException e) {
                            player.sendMessage(ChatColor.RED + "搜索参数错误");
                        }
                    } else if (title.startsWith("§6玩家:")) {
                        String playerName = extractSearchPlayer(title);
                        @SuppressWarnings("deprecation")
                        OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(playerName);
                        if (targetPlayer != null && targetPlayer.hasPlayedBefore()) {
                            guiManager.openPlayerSearchGUI(player, targetPlayer.getUniqueId(), playerName, currentPage - 1);
                        }
                    }
                }
            } else if (clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().equals("§e下一页")) {
                int currentPage = extractPageNumber(title);
                player.closeInventory();
                
                if (title.startsWith("§6搜索:")) {
                    String materialName = extractSearchMaterial(title);
                    try {
                        Material targetMaterial = Material.valueOf(materialName.toUpperCase());
                        guiManager.openItemSearchGUI(player, targetMaterial, currentPage + 1);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "搜索参数错误");
                    }
                } else if (title.startsWith("§6玩家:")) {
                    String playerName = extractSearchPlayer(title);
                    @SuppressWarnings("deprecation")
                    OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(playerName);
                    if (targetPlayer != null && targetPlayer.hasPlayedBefore()) {
                        guiManager.openPlayerSearchGUI(player, targetPlayer.getUniqueId(), playerName, currentPage + 1);
                    }
                }
            }
        }
        
        // 处理关闭按钮
        if (clickedItem.getType() == Material.BARRIER && 
            clickedItem.getItemMeta() != null && 
            clickedItem.getItemMeta().getDisplayName().equals("§c关闭")) {
            player.closeInventory();
            return;
        }
        
        // 处理物品购买或下架（与主市场相同）
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            for (String lore : clickedItem.getItemMeta().getLore()) {
                if (lore.contains("ID: ")) {
                    String idString = lore.substring(lore.lastIndexOf(" ") + 1);
                    try {
                        UUID listingId = UUID.fromString(idString);
                        MarketListing listing = plugin.getMarketManager().getListing(listingId);
                        if (listing == null) {
                            player.sendMessage(ChatColor.RED + "该物品已不存在!");
                            return;
                        }

                        // 检查是否是卖家自己的物品
                        if (listing.getSellerId().equals(player.getUniqueId())) {
                            // Shift+左键下架到邮箱
                            if (event.isShiftClick() && event.isLeftClick()) {
                                player.closeInventory();
                                plugin.getMarketManager().removeListingToMailbox(listingId, player);
                                // 重新打开当前搜索GUI显示更新后的列表
                                int currentPage = extractPageNumber(title);
                                
                                if (title.startsWith("§6搜索:")) {
                                    String materialName = extractSearchMaterial(title);
                                    try {
                                        Material targetMaterial = Material.valueOf(materialName.toUpperCase());
                                        guiManager.openItemSearchGUI(player, targetMaterial, currentPage);
                                    } catch (IllegalArgumentException e) {
                                        player.sendMessage(ChatColor.RED + "搜索参数错误");
                                    }
                                } else if (title.startsWith("§6玩家:")) {
                                    String playerName = extractSearchPlayer(title);
                                    @SuppressWarnings("deprecation")
                                    OfflinePlayer targetPlayer = plugin.getServer().getOfflinePlayer(playerName);
                                    if (targetPlayer != null && targetPlayer.hasPlayedBefore()) {
                                        guiManager.openPlayerSearchGUI(player, targetPlayer.getUniqueId(), playerName, currentPage);
                                    }
                                }
                                return;
                            } else {
                                // 普通点击提示
                                player.sendMessage(ChatColor.YELLOW + "Shift+左键点击可下架此物品到邮箱!");
                                return;
                            }
                        }

                        // 普通购买流程
                        pendingPurchases.put(player.getUniqueId(), listingId);
                        player.closeInventory();
                        guiManager.openConfirmGUI(player, listingId);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "无效的物品ID!");
                    }
                    break;
                }
            }
        }
    }
    
    private int extractPageNumber(String title) {
        try {
            String pageStr = title.substring(title.lastIndexOf("第") + 1, title.lastIndexOf("页"));
            return Integer.parseInt(pageStr) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private String extractSearchMaterial(String title) {
        try {
            // 格式: "搜索: DIAMOND - 第1页"
            String searchPart = title.substring(title.indexOf("搜索: ") + 4, title.lastIndexOf(" - 第"));
            return searchPart.trim();
        } catch (Exception e) {
            return "";
        }
    }
    
    private String extractSearchPlayer(String title) {
        try {
            // 格式: "玩家: PlayerName 的上架物品 - 第1页"
            String searchPart = title.substring(title.indexOf("玩家: ") + 4, title.lastIndexOf(" 的上架物品 - 第"));
            return searchPart.trim();
        } catch (Exception e) {
            return "";
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            pendingPurchases.remove(player.getUniqueId());
        }
    }
}