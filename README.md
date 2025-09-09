# GlobalMarket 全球市场插件

一个功能强大的Bukkit/Spigot服务器全球市场插件，提供安全、高效的玩家间交易系统。

## 🌟 功能特性

### 核心功能
- **全球市场** - 所有服务器玩家共享的统一市场
- **离线交易** - 支持玩家不在线时的物品交易
- **经济系统** - 完美兼容Vault经济系统
- **物品验证** - 智能物品验证机制，防止作弊物品
- **独立库存** - 无需OpenInv依赖的独立离线库存系统

### 安全特性
- **反作弊保护** - 严格的物品验证和反作弊机制
- **数据持久化** - 自动保存玩家邮箱和市场数据
- **异常处理** - 完善的错误处理和日志记录

## 📦 安装指南

### 前置要求
- Java 21或更高版本
- Bukkit/Spigot 1.20+
- Vault插件（用于经济系统）

### 安装步骤
1. 下载`GlobalMarket-4.3.1.jar`
2. 将jar文件放入服务器的`plugins`文件夹
3. 启动服务器，插件将自动生成配置文件
4. 根据需要修改`config.yml`
5. 重启服务器完成安装

## ⚙️ 配置说明

### config.yml
```yaml
# 经济系统设置
economy:
  enabled: true
  
# 市场设置
market:
  max-listings: 100  # 最大上架物品数
  tax: 0.05          # 交易税率（5%）
  
# 存储设置
storage:
  auto-save: true
  save-interval: 300  # 自动保存间隔（秒）
```

## 🎮 使用方法

### 基础命令
```
/market listing           - 打开全球市场界面
/market create <总价> [数量]        - 将手中物品上架出售
/gm mail         - 查看个人邮箱
/gm reload       - 重载插件配置（管理员）
```

### 权限节点
```
globalmarket.use          - 使用全球市场
globalmarket.sell         - 允许出售物品
globalmarket.buy          - 允许购买物品
globalmarket.admin        - 管理员权限
```

## 📊 数据存储

### 文件结构
```
plugins/GlobalMarket/
├── config.yml          # 主配置文件
├── data/
│   ├── market.db       # 市场数据
│   └── mailboxes/      # 玩家邮箱数据
└── logs/
    └── latest.log      # 运行日志
```

### 备份建议
- 定期备份`data/`文件夹
- 建议在服务器维护期间进行备份
- 支持热备份，无需停服

## 🔧 开发信息

### 技术栈
- **Java 21** - 主要开发语言
- **Maven** - 构建工具
- **Bukkit API** - Minecraft服务端API
- **Vault API** - 经济系统API

### 构建项目
```bash
# 克隆项目
git clone [项目地址]

# 构建JAR
cd GlobalMarket
mvn clean package

# 输出文件
target/GlobalMarket-4.3.1.jar
```

### 插件API
```java
// 获取GlobalMarket实例
GlobalMarket plugin = GlobalMarket.getInstance();

// 获取经济管理器
EconomyManager economy = plugin.getEconomyManager();

// 获取市场管理器
MarketManager market = plugin.getMarketManager();
```

## 🐛 故障排除

### 常见问题

**Q: 插件无法加载**
- 检查Java版本是否为21+
- 确认Vault插件已安装
- 查看控制台错误日志

**Q: 经济系统无法启用**
- 确认已安装经济插件（如EssentialsX Economy）
- 检查Vault是否正常工作
- 在config.yml中启用经济系统

**Q: 物品无法上架**
- 检查物品是否符合规则
- 确认拥有`globalmarket.sell`权限
- 查看是否有足够的上架槽位

### 日志查看
插件会在控制台输出关键信息：
```
[GlobalMarket] 经济系统已启用: EssentialsX Economy
```

## 📄 更新日志

### v4.3.1
- 优化日志输出，减少控制台冗余信息
- 提升物品验证器性能
- 修复若干稳定性问题

### v4.3.0
- 新增独立离线库存系统
- 改进物品验证机制
- 优化数据存储性能

## 📞 支持联系

### 获取帮助
- **GitHub Issues**: [提交问题](https://github.com/HotWaterFlask/GlobalMarket/issues)

### 贡献代码
欢迎提交Pull Request！请遵循以下规范：
1. 代码需通过测试
2. 添加适当的注释
3. 更新相关文档

## 📄 许可证

本项目采用MIT许可证，详见[LICENSE](LICENSE)文件。

---

**GlobalMarket** - 让交易更简单，让经济更繁荣！
