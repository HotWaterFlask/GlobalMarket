package com.globalmarket;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public class EconomyManager {
    
    private final GlobalMarket plugin;
    private final Logger logger;
    private Economy economy;
    private boolean enabled;
    
    public EconomyManager(GlobalMarket plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.enabled = false;
    }
    
    public boolean setupEconomy() {
        if (!plugin.getConfig().getBoolean("use-economy", false)) {
            logger.info("经济系统已禁用，在config.yml中启用");
            return false;
        }
        
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("未找到Vault插件，经济系统无法启用");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("未找到经济插件，经济系统无法启用");
            return false;
        }
        
        economy = rsp.getProvider();
        enabled = true;
        logger.info("经济系统已启用: " + economy.getName());
        return true;
    }
    
    public boolean isEnabled() {
        return enabled && economy != null;
    }
    
    public boolean hasBalance(OfflinePlayer player, double amount) {
        if (!isEnabled()) return true; // 如果经济系统未启用，允许交易
        return economy.has(player, amount);
    }
    
    public EconomyResponse withdraw(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, "");
        }
        return economy.withdrawPlayer(player, amount);
    }
    
    public EconomyResponse deposit(OfflinePlayer player, double amount) {
        if (!isEnabled()) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, "");
        }
        return economy.depositPlayer(player, amount);
    }
    
    public double getBalance(OfflinePlayer player) {
        if (!isEnabled()) return 0;
        return economy.getBalance(player);
    }
    
    public String formatCurrency(double amount) {
        if (!isEnabled()) return "$" + String.format("%.2f", amount);
        return economy.format(amount);
    }
    
    public String getCurrencyName() {
        if (!isEnabled()) return "金币";
        return economy.currencyNameSingular();
    }
    
    public boolean processPurchase(OfflinePlayer buyer, OfflinePlayer seller, double amount) {
        if (!isEnabled()) {
            return true; // 经济系统未启用时允许交易
        }
        
        double taxRate = plugin.getConfig().getDouble("transaction-tax", 0) / 100.0;
        double taxAmount = amount * taxRate;
        double sellerAmount = amount - taxAmount;
        
        if (!hasBalance(buyer, amount)) {
            return false;
        }
        
        EconomyResponse withdrawResponse = withdraw(buyer, amount);
        if (!withdrawResponse.transactionSuccess()) {
            logger.warning("从买家扣款失败: " + buyer.getName() + " - " + withdrawResponse.errorMessage);
            return false;
        }
        
        EconomyResponse depositResponse = deposit(seller, sellerAmount);
        if (!depositResponse.transactionSuccess()) {
            // 如果存入失败，退回买家
            deposit(buyer, amount);
            logger.warning("向卖家存款失败: " + seller.getName() + " - " + depositResponse.errorMessage);
            return false;
        }
        
        if (taxAmount > 0) {
            logger.info("交易税: " + formatCurrency(taxAmount));
        }
        
        return true;
    }
    
    public void sendBalanceInfo(Player player) {
        if (!isEnabled()) {
            player.sendMessage(ChatColor.YELLOW + "经济系统未启用");
            return;
        }
        
        double balance = getBalance(player);
        player.sendMessage(ChatColor.GREEN + "你的余额: " + ChatColor.GOLD + formatCurrency(balance));
    }
}