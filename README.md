# GlobalMarket - Minecraft全球市场插件

一个为Paper 1.21.8服务器设计的全球市场插件，允许玩家在游戏中创建、购买和管理物品交易。

## 功能特性

- ✅ **现代化图形界面** - 完整的GUI体验，支持分页和排序
- ✅ **四向排序系统** - 最新优先、最早优先、低价优先、高价优先
- ✅ **搜索功能** - 支持物品ID和玩家名称搜索
- ✅ **玩家独立排序** - 每个玩家的排序偏好独立保存
- ✅ **实时动态更新** - 新物品上架/下架立即生效
- ✅ **现代化下架系统** - Shift+点击物品即可下架
- ✅ **邮箱系统** - 购买物品和售出资金通过邮箱发放
- ✅ **交易记录成书** - 详细的交易记录和资金到账确认
- ✅ **上架收费系统** - 防止广告物品上架，按物品价格百分比收费
- ✅ **数据持久化** - 支持YAML、MySQL、PostgreSQL存储
- ✅ **经济系统集成** - 完整的Vault API支持
- ✅ **权限系统** - 细粒度的权限控制
- ✅ **多语言支持** - 可自定义所有消息文本

## 安装方法

1. 下载插件jar文件
2. 将jar文件放入服务器的 `plugins` 文件夹
3. 重启服务器或使用 `/reload` 命令
4. 配置文件将自动生成在 `plugins/GlobalMarket/` 文件夹中

## 使用方法

### 基本命令

```
/market create <总价> [数量] - 创建上架物品（不填数量则上架全部手持物品）
/market listing        - 查看实时更新的市场列表
/market search <物品ID> - 搜索特定物品（如: /market search diamond）
/market search <玩家名> - 搜索特定玩家的物品（如: /market search Steve）
/market mailbox        - 打开邮箱查看购买物品、售出资金和下架物品
/market reload         - 重新加载插件（管理员）
```

### 现代化下架功能
#### 通过GUI下架
1. **打开市场界面**: 使用 `/market listing` 或 `/market search`
2. **查找自己上架的物品**: 物品会显示红色提示
3. **下架物品**: Shift+左键点击自己上架的物品
4. **物品存入邮箱**: 下架的物品会立即存入邮箱

#### 通过邮箱管理
1. **打开邮箱**: 使用 `/market mailbox`
2. **识别下架物品**: 邮箱中显示为 [已下架] 标记
3. **取回物品**: 点击下架物品将其取回背包
4. **背包空间检查**: 系统会自动检查背包空间

### 权限节点

- `globalmarket.use` - 使用市场命令
- `globalmarket.sell` - 出售物品
- `globalmarket.buy` - 购买物品
- `globalmarket.remove` - 下架物品
- `globalmarket.gui` - 使用图形界面（所有GUI功能）
- `globalmarket.reload` - 重新加载插件

## 配置文件

配置文件位于 `plugins/GlobalMarket/config.yml`，包含以下选项：

- `max-listings-per-player` - 每个玩家的最大上架数量
- `use-economy` - 是否启用经济系统
- `transaction-tax` - 交易税百分比
- `listing-expire-time` - 物品过期时间
- `mailbox.items-per-page` - 邮箱每页显示物品数量
- `mailbox.transaction-record-title` - 交易记录书名
- `mailbox.transaction-record-author` - 交易记录作者

## 开发构建

### 环境要求

- Java 21+
- Maven 3.6+
- Paper 1.21.8

### 构建步骤

1. 克隆项目
2. 运行 `mvn clean package`
3. 生成的jar文件位于 `target/GlobalMarket-1.0.0.jar`

## 技术支持

如果遇到问题，请检查：
1. 服务器版本是否为 Paper 1.21.8
2. Java版本是否为21或更高
3. 配置文件是否正确
4. 查看控制台错误日志

## 更新日志

### v1.0.0
- 初始版本发布
- 基础市场功能
- 数据持久化
- 权限系统

## 许可证

MIT License - 详见 LICENSE 文件