# GlobalMarket 数据库配置快速指南

## 🚀 快速开始

### 1. 选择存储方式
GlobalMarket 支持三种存储方式：
- **YAML**（默认）：适合小型服务器，无需配置
- **MySQL**：适合中型服务器，需要数据库
- **PostgreSQL**：适合大型服务器，需要数据库

### 2. YAML存储（默认）
无需额外配置，数据保存在 `plugins/GlobalMarket/market_data.yml`

### 3. MySQL配置步骤

#### 安装MySQL
```bash
# Windows
# 下载并安装MySQL Community Server
# 记住设置的root密码

# Linux (Ubuntu/Debian)
sudo apt update
sudo apt install mysql-server mysql-client

# 启动MySQL
sudo systemctl start mysql
sudo systemctl enable mysql
```

#### 创建数据库和用户
```sql
-- 登录MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE globalmarket CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER 'minecraft'@'localhost' IDENTIFIED BY 'yourpassword';
GRANT ALL PRIVILEGES ON globalmarket.* TO 'minecraft'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

#### 修改配置
编辑 `plugins/GlobalMarket/config.yml`：
```yaml
storage-type: mysql

database:
  mysql:
    host: localhost
    port: 3306
    database: globalmarket
    username: minecraft
    password: yourpassword
    ssl: false
    pool-size: 10
```

### 4. PostgreSQL配置步骤

#### 安装PostgreSQL
```bash
# Windows
# 下载并安装PostgreSQL

# Linux (Ubuntu/Debian)
sudo apt update
sudo apt install postgresql postgresql-contrib

# 启动PostgreSQL
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

#### 创建数据库和用户
```bash
# 切换到postgres用户
sudo -u postgres psql

-- 创建数据库
CREATE DATABASE globalmarket WITH ENCODING 'UTF8';

-- 创建用户
CREATE USER minecraft WITH PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE globalmarket TO minecraft;
\q
```

#### 修改配置
编辑 `plugins/GlobalMarket/config.yml`：
```yaml
storage-type: postgresql

database:
  postgresql:
    host: localhost
    port: 5432
    database: globalmarket
    username: minecraft
    password: yourpassword
    ssl: false
    pool-size: 10
```

### 5. 验证配置

启动服务器后，查看控制台输出：
- ✅ `数据库连接成功: mysql` 或 `数据库连接成功: postgresql`
- ✅ `数据库表创建成功`

### 6. 数据迁移

#### 从YAML迁移到数据库
当前版本需要手动迁移：
1. 备份 `market_data.yml`
2. 修改配置为数据库模式
3. 重新启动服务器
4. 使用管理命令重新创建列表

### 7. 性能对比

| 存储方式 | 记录数量 | 性能 | 备份难度 | 推荐场景 |
|---------|----------|------|----------|----------|
| YAML | < 1,000 | 优秀 | 简单 | 小型服务器 |
| MySQL | 1,000 - 10,000 | 良好 | 中等 | 中型服务器 |
| PostgreSQL | > 10,000 | 优秀 | 中等 | 大型服务器 |

### 8. 故障排除

#### 连接失败
- 检查数据库服务是否启动
- 验证用户名密码是否正确
- 检查防火墙设置
- 确认端口是否正确

#### 表创建失败
- 检查用户是否有创建表的权限
- 确认数据库字符集为utf8mb4（MySQL）或UTF8（PostgreSQL）

### 9. 监控命令

```bash
# 查看数据库连接状态
/market reload

# 查看当前存储类型
# 查看控制台日志输出
```

## 🎯 最佳实践

1. **小型服务器**（<50人）：使用YAML存储（零配置，开箱即用）
2. **中型服务器**（50-200人）：使用MySQL存储
3. **大型服务器**（>200人）：使用PostgreSQL存储
4. **备份策略**：定期备份数据库或YAML文件
5. **监控**：监控数据库连接池状态和性能
6. **迁移策略**：YAML到数据库迁移建议在新服务器上实施

## 📊 数据库表结构

插件会自动创建以下表：
- `market_listings`：市场物品列表
- `market_transactions`：交易记录