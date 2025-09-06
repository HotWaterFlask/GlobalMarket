package com.globalmarket;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

public class DatabaseManager {
    
    private final GlobalMarket plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private String storageType;
    
    public DatabaseManager(GlobalMarket plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    public boolean initialize() {
        FileConfiguration config = plugin.getConfig();
        storageType = config.getString("storage-type", "yaml").toLowerCase();
        
        if (storageType.equals("mysql") || storageType.equals("postgresql")) {
            return setupDatabase(config);
        }
        
        // YAML存储不需要数据库连接
        return true;
    }
    
    private boolean setupDatabase(FileConfiguration config) {
        try {
            ConfigurationSection dbConfig = config.getConfigurationSection("database." + storageType);
            if (dbConfig == null) {
                logger.severe("数据库配置缺失: " + storageType);
                return false;
            }
            
            HikariConfig hikariConfig = new HikariConfig();
            
            // 设置数据库连接信息
            if (storageType.equals("mysql")) {
                hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                        dbConfig.getString("host"),
                        dbConfig.getInt("port"),
                        dbConfig.getString("database")));
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (storageType.equals("postgresql")) {
                hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
                        dbConfig.getString("host"),
                        dbConfig.getInt("port"),
                        dbConfig.getString("database")));
                hikariConfig.setDriverClassName("org.postgresql.Driver");
            }
            
            hikariConfig.setUsername(dbConfig.getString("username"));
            hikariConfig.setPassword(dbConfig.getString("password"));
            
            // SSL配置
            if (!dbConfig.getBoolean("ssl", false)) {
                Properties props = new Properties();
                props.setProperty("useSSL", "false");
                props.setProperty("verifyServerCertificate", "false");
                hikariConfig.setDataSourceProperties(props);
            }
            
            // 连接池配置
            hikariConfig.setMaximumPoolSize(dbConfig.getInt("pool-size", 10));
            hikariConfig.setConnectionTimeout(dbConfig.getLong("connection-timeout", 30000));
            hikariConfig.setIdleTimeout(dbConfig.getLong("idle-timeout", 600000));
            hikariConfig.setMaxLifetime(dbConfig.getLong("max-lifetime", 1800000));
            
            // 连接测试
            hikariConfig.setConnectionTestQuery("SELECT 1");
            
            dataSource = new HikariDataSource(hikariConfig);
            
            // 测试连接
            try (Connection conn = getConnection()) {
                if (conn != null) {
                    createTables();
                    logger.info("数据库连接成功: " + storageType);
                    return true;
                }
            }
            
        } catch (Exception e) {
            logger.severe("数据库连接失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }
    
    private void createTables() {
        String createListingsTable = """
            CREATE TABLE IF NOT EXISTS market_listings (
                id VARCHAR(36) PRIMARY KEY,
                seller_uuid VARCHAR(36) NOT NULL,
                seller_name VARCHAR(16) NOT NULL,
                item_base64 TEXT NOT NULL,
                price DECIMAL(10,2) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
            
        String createTransactionsTable = """
            CREATE TABLE IF NOT EXISTS market_transactions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                listing_id VARCHAR(36) NOT NULL,
                seller_uuid VARCHAR(36) NOT NULL,
                buyer_uuid VARCHAR(36) NOT NULL,
                buyer_name VARCHAR(32) NOT NULL,
                item_base64 TEXT NOT NULL,
                price DECIMAL(15,2) NOT NULL,
                transaction_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_listing (listing_id),
                INDEX idx_seller (seller_uuid),
                INDEX idx_buyer (buyer_uuid),
                INDEX idx_time (transaction_time)
            )
            """;
        // PostgreSQL语法调整
        if (storageType.equals("postgresql")) {
            createListingsTable = createListingsTable
                .replace("AUTO_INCREMENT", "SERIAL")
                .replace("DECIMAL(10,2)", "NUMERIC(10,2)");
            
            createTransactionsTable = createTransactionsTable
                .replace("AUTO_INCREMENT", "SERIAL")
                .replace("DECIMAL(15,2)", "NUMERIC(15,2)");
        }
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute(createListingsTable);
            stmt.execute(createTransactionsTable);
            
            logger.info("数据库表创建成功");
            
        } catch (SQLException e) {
            logger.severe("创建数据库表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public Connection getConnection() throws SQLException {
        return dataSource != null ? dataSource.getConnection() : null;
    }
    
    public String getStorageType() {
        return storageType;
    }
    
    public boolean isDatabaseEnabled() {
        return storageType.equals("mysql") || storageType.equals("postgresql");
    }
    
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("数据库连接已关闭");
        }
    }
}