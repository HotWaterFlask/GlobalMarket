package com.globalmarket;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.sql.*;
import java.util.*;

public class DatabaseStorage {
    
    private final GlobalMarket plugin;
    private final DatabaseManager databaseManager;
    
    public DatabaseStorage(GlobalMarket plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }
    
    // 保存单个市场列表
    public void saveListing(MarketListing listing) {
        if (!databaseManager.isDatabaseEnabled()) {
            return;
        }
        
        String insertSQL = """
            INSERT INTO market_listings (id, seller_uuid, seller_name, item_base64, price, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            
            stmt.setString(1, listing.getListingId().toString());
            stmt.setString(2, listing.getSellerId().toString());
            stmt.setString(3, "Unknown");
            stmt.setString(4, itemStackToBase64(listing.getItem()));
            stmt.setBigDecimal(5, new java.math.BigDecimal(listing.getPrice()));
            stmt.setTimestamp(6, new Timestamp(listing.getCreatedAt()));
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("保存市场列表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 加载市场列表
    public Map<UUID, MarketListing> loadListings() {
        Map<UUID, MarketListing> listings = new HashMap<>();
        
        if (!databaseManager.isDatabaseEnabled()) {
            return listings;
        }
        
        String selectSQL = """
            SELECT id, seller_uuid, seller_name, item_base64, price, created_at
            FROM market_listings
            ORDER BY created_at DESC
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectSQL);
             ResultSet rs = stmt.executeQuery()) {
            
            int count = 0;
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID sellerUUID = UUID.fromString(rs.getString("seller_uuid"));
                ItemStack item = itemStackFromBase64(rs.getString("item_base64"));
                double price = rs.getBigDecimal("price").doubleValue();
                long createdAt = rs.getTimestamp("created_at").getTime();
                
                MarketListing listing = new MarketListing(id, sellerUUID, item, price, createdAt);
                listings.put(id, listing);
                count++;
            }
            
            // **关键调试：添加数据库加载的详细日志**
            plugin.getLogger().info("[数据库操作] 从数据库加载物品数量: " + count);
            
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("加载市场列表失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return listings;
    }
    
    // 移除市场列表
    public void removeListing(UUID listingId) {
        if (!databaseManager.isDatabaseEnabled()) {
            return;
        }
        
        String deleteSQL = "DELETE FROM market_listings WHERE id = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(deleteSQL)) {
            
            stmt.setString(1, listingId.toString());
            int affectedRows = stmt.executeUpdate();
            
            // **关键调试：添加数据库删除操作的详细日志**
            plugin.getLogger().info("[数据库操作] 尝试删除物品ID: " + listingId);
            plugin.getLogger().info("[数据库操作] 影响行数: " + affectedRows);
            
            if (affectedRows > 0) {
                plugin.getLogger().info("[数据库操作] 物品已从数据库成功删除: " + listingId);
            } else {
                plugin.getLogger().warning("[数据库操作] 未找到要删除的物品ID: " + listingId);
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("移除市场列表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 记录交易
    public void recordTransaction(UUID listingId, UUID sellerUUID, UUID buyerUUID, String buyerName, ItemStack item, double price) {
        if (!databaseManager.isDatabaseEnabled()) {
            return;
        }
        
        String insertSQL = """
            INSERT INTO market_transactions (listing_id, seller_uuid, buyer_uuid, buyer_name, item_base64, price)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {
            
            stmt.setString(1, listingId.toString());
            stmt.setString(2, sellerUUID.toString());
            stmt.setString(3, buyerUUID.toString());
            stmt.setString(4, buyerName);
            stmt.setString(5, itemStackToBase64(item));
            stmt.setBigDecimal(6, new java.math.BigDecimal(price));
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("记录交易失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // 获取玩家上架数量
    public int getPlayerListingCount(UUID playerUUID) {
        if (!databaseManager.isDatabaseEnabled()) {
            return 0;
        }
        
        String countSQL = """
            SELECT COUNT(*) as count
            FROM market_listings
            WHERE seller_uuid = ? AND (expires_at IS NULL OR expires_at > NOW())
            """;
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(countSQL)) {
            
            stmt.setString(1, playerUUID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家上架数量失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return 0;
    }
    
    // 工具方法：ItemStack转Base64
    private String itemStackToBase64(ItemStack item) throws IllegalStateException {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("无法将物品转换为Base64", e);
        }
    }
    
    // 工具方法：Base64转ItemStack
    private ItemStack itemStackFromBase64(String data) throws IOException {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException e) {
            throw new IOException("无法解析Base64物品数据", e);
        }
    }
}