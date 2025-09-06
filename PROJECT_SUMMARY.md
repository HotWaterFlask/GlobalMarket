# GlobalMarket 项目总结

## 项目概述

这是一个为Minecraft Paper 1.21.8服务器设计的全球市场插件，允许玩家在游戏中进行物品交易。

## 项目结构

```
GlobalMarket/
├── src/main/java/com/globalmarket/
│   ├── GlobalMarket.java      # 主插件类
│   ├── MarketManager.java     # 市场管理器
│   ├── MarketListing.java     # 市场列表数据类
│   ├── MarketCommand.java     # 命令处理
│   └── MarketListener.java    # 事件监听器
├── src/main/resources/
│   ├── plugin.yml             # 插件配置
│   └── config.yml             # 默认配置
├── pom.xml                    # Maven配置
├── build.bat                  # Windows构建脚本
├── test-setup.bat             # 测试环境设置
├── README.md                  # 项目文档
├── BUILD.md                   # 构建说明
└── PROJECT_SUMMARY.md         # 本文件
```

## 功能特性

### 已实现功能
- ✅ 现代化图形界面 (GUI) - 支持分页、排序、搜索
- ✅ 四向排序系统 - 最新/最早/低价/高价优先
- ✅ 搜索功能 - 支持物品ID和玩家名称搜索
- ✅ 玩家独立排序偏好 - 每个玩家独立保存排序设置
- ✅ 实时动态更新 - 新物品上架/下架立即生效
- ✅ 现代化下架系统 - Shift+点击物品即可下架
- ✅ 物品上架出售
- ✅ 物品购买
- ✅ 市场列表查看
- ✅ 数据持久化存储 - 支持YAML/MySQL/PostgreSQL
- ✅ 权限系统
- ✅ 配置文件支持
- ✅ 命令自动补全
- ✅ 经济系统支持 (Vault API)
- ✅ 上架收费系统

### 命令系统
- `/market create <总价> [数量]` - 创建上架物品
- `/market listing` - 查看实时更新的市场列表
- `/market search <物品ID>` - 搜索特定物品
- `/market search <玩家名>` - 搜索特定玩家的物品
- `/market mailbox` - 打开邮箱系统
- `/market reload` - 重新加载插件

### 权限系统
- `globalmarket.use` - 基础使用权限
- `globalmarket.sell` - 出售物品权限
- `globalmarket.buy` - 购买物品权限
- `globalmarket.remove` - 下架物品权限
- `globalmarket.gui` - 使用图形界面权限
- `globalmarket.reload` - 重新加载权限

## 技术栈

- **Java 21** - 编程语言
- **Paper 1.21.8 API** - Minecraft服务器API
- **Maven** - 构建工具
- **YAML** - 数据存储格式

## 使用步骤

1. **构建插件**
   - 安装Maven
   - 运行 `mvn clean package`
   - 或使用 `build.bat`

2. **安装插件**
   - 将 `GlobalMarket-1.0.0.jar` 复制到服务器 `plugins/` 目录
   - 重启服务器

3. **配置插件**
   - 编辑 `plugins/GlobalMarket/config.yml`
   - 使用 `/market reload` 重载配置

4. **使用插件**
   - 在游戏中使用 `/market` 命令

## 扩展建议

### 扩展建议

#### 未来功能
- 💡 物品分类系统
- 💡 拍卖系统
- 💡 物品评价系统
- 💡 移动端支持（Web界面）
- 💡 批量操作优化
- 💡 高级搜索过滤器
- 💡 价格趋势图表
- 💡 交易统计报表

#### 性能优化
- 💡 缓存系统优化
- 💡 异步处理增强
- 💡 数据库索引优化
- 💡 批量查询优化

## 已知限制

- 当前版本已支持完整功能，但大型服务器建议切换到数据库模式
- 首次使用数据库需要配置连接信息
- 旧版本YAML数据迁移到数据库需要手动处理

## 开发状态

✅ **已完成** - 完整功能实现
✅ **已优化** - 现代化用户体验和性能
✅ **已部署** - 生产就绪版本

## 联系支持

如有问题或建议，请通过以下方式联系：
- GitHub Issues
- 项目文档
- 社区论坛