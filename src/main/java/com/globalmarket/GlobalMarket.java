package com.globalmarket;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.ChatColor;
// 独立离线库存系统
import com.globalmarket.util.SimpleOfflineInventory;
import com.globalmarket.util.EnhancedRollbackManager;

import java.util.logging.Logger;

public class GlobalMarket extends JavaPlugin {
    
    private static GlobalMarket instance;
    private Logger logger;
    private EconomyManager economyManager;
    private MarketManager marketManager;
    private GUIManager guiManager;
    private EnhancedRollbackManager enhancedRollbackManager;
    
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
        
        // 初始化增强回滚管理器
        enhancedRollbackManager = new EnhancedRollbackManager(this);
        
        // 初始化独立离线库存系统
        SimpleOfflineInventory.getInstance();
        getLogger().info("已启用独立离线库存系统，无需OpenInv依赖");
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MarketListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this, guiManager), this);
        
        // 注册命令
        MarketCommand marketCommand = new MarketCommand(this);
        getCommand("market").setExecutor(marketCommand);
        getCommand("market").setTabCompleter(marketCommand);
        
        // 加载邮箱数据
        if (marketManager != null && marketManager.getMailbox() != null) {
            marketManager.getMailbox().loadMailboxData();
        }
        
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
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public MarketManager getMarketManager() {
        return marketManager;
    }
    
    public GUIManager getGUIManager() {
        return guiManager;
    }
    
    public EnhancedRollbackManager getEnhancedRollbackManager() {
        return enhancedRollbackManager;
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