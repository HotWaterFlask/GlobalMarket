package com.globalmarket;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;

import java.util.logging.Logger;

public class GlobalMarket extends JavaPlugin {
    
    private static GlobalMarket instance;
    private Logger logger;
    private MarketManager marketManager;
    private EconomyManager economyManager;
    private GUIManager guiManager;
    
    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化经济系统
        economyManager = new EconomyManager(this);
        economyManager.setupEconomy();
        
        // 初始化市场管理器
        marketManager = new MarketManager(this);
        
        // 初始化GUI管理器
        guiManager = new GUIManager(this);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MarketListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this, guiManager), this);
        
        // 注册命令
        MarketCommand marketCommand = new MarketCommand(this);
        getCommand("market").setExecutor(marketCommand);
        getCommand("market").setTabCompleter(marketCommand);
        
        logger.info("GlobalMarket 插件已启用!");
        logger.info("作者: GlobalMarket 团队");
        logger.info("版本: " + getDescription().getVersion());
        logger.info("经济系统: " + (economyManager.isEnabled() ? "已启用" : "已禁用"));
    }
    
    @Override
    public void onDisable() {
        if (marketManager != null) {
            marketManager.close();
        }
        logger.info("GlobalMarket 插件已禁用!");
    }
    
    public static GlobalMarket getInstance() {
        return instance;
    }
    
    public MarketManager getMarketManager() {
        return marketManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public GUIManager getGuiManager() {
        return guiManager;
    }
    
    public void reloadPlugin() {
        reloadConfig();
        if (marketManager != null) {
            marketManager.reload();
        }
        if (economyManager != null) {
            economyManager.setupEconomy();
        }
    }
}