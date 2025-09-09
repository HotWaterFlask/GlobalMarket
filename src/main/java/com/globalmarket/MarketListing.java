package com.globalmarket;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.UUID;

public class MarketListing implements Serializable {
    
    private final UUID listingId;
    private final UUID sellerId;
    private final String itemBase64;
    private final double price;
    private final long createdAt;
    
    public MarketListing(UUID listingId, UUID sellerId, ItemStack item, double price, long createdAt) {
        this.listingId = listingId;
        this.sellerId = sellerId;
        this.itemBase64 = item != null ? itemStackToBase64(item) : null;
        this.price = price;
        this.createdAt = createdAt;
    }
    
    // 用于从存储加载的构造函数
    public MarketListing(UUID listingId, UUID sellerId, String itemBase64, double price, long createdAt) {
        this.listingId = listingId;
        this.sellerId = sellerId;
        this.itemBase64 = itemBase64;
        this.price = price;
        this.createdAt = createdAt;
    }
    
    public UUID getListingId() {
        return listingId;
    }
    
    public UUID getSellerId() {
        return sellerId;
    }
    
    public ItemStack getItem() {
        return itemStackFromBase64(itemBase64);
    }
    
    public double getPrice() {
        return price;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public String getItemBase64() {
        return itemBase64;
    }
    
    private static String itemStackToBase64(ItemStack item) {
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
    
    private static ItemStack itemStackFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("无法从Base64转换物品", e);
        }
    }
}