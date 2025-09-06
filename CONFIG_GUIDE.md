# GlobalMarket 配置指南

## 上架收费功能配置

### 启用上架收费

在 `config.yml` 中配置上架费用：

```yaml
# 上架费用百分比 (物品价格的百分比，0为免费)
listing-fee-percentage: 0.5
# 上架费用计算方式: round (四舍五入) 或 floor (去尾法/向下取整)
listing-fee-calculation-method: floor
```

- **listing-fee-percentage**:
  - **0**: 关闭上架收费功能
  - **0.1-100**: 设置上架费用百分比
  - **推荐值**: 0.5% 平衡防广告效果和用户体验

- **listing-fee-calculation-method**:
  - **round**: 四舍五入法，计算结果更精确
  - **floor**: 去尾法，向下取整，更加严格
  - **推荐值**: floor 对防广告更有效

### 数据存储配置
支持三种存储方式：YAML、MySQL、PostgreSQL

#### 存储类型选择
```yaml
# 数据存储类型: yaml, mysql, postgresql
storage-type: yaml
```

#### YAML存储（默认）
适合中小型服务器，无需额外配置，数据存储在 `market_data.yml` 文件中。

#### MySQL配置
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

#### PostgreSQL配置
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

#### 数据库初始化
首次使用数据库时，插件会自动创建所需的表结构：
- `market_listings` - 市场物品列表
- `market_transactions` - 交易记录

### 费用计算示例

- 物品价格：1,000,000 金币
- 上架费用比例：0.5%
- 实际收取费用：1,000,000 × 0.005 = 5,000 金币

### 防广告机制

当玩家尝试上架广告物品（如命名为"美女直播：www.3fbh9nk.com"的物品）时：

1. **高额上架费用**：价格9999999金币的广告物品需要支付 9999999 × 0.005 = 49,999.995 金币的上架费用
2. **经济门槛**：玩家必须拥有足够的金币才能支付上架费用
3. **成本效益**：广告物品的高额上架费用使得恶意上架变得不划算

### 消息配置

可以自定义上架收费相关的消息：

```yaml
messages:
  listing-fee-charged: "&a已收取上架费用: %fee% (物品价格的 %percentage%%)"
  sale-success: "&a物品上架成功! 价格: %amount%"
```

### 关闭上架收费

如需关闭上架收费功能，将配置设置为：

```yaml
listing-fee-percentage: 0
```

### 推荐配置

对于防止广告物品上架，推荐使用：

```yaml
listing-fee-percentage: 0.5  # 0.5%的费用比例
max-listings-per-player: 5   # 限制每个玩家的上架数量
listing-expire-time: 12      # 12小时后自动下架
```

## 邮箱系统配置

### 邮箱功能说明
邮箱系统是一个现代化的物品和资金管理平台，支持：
- 购买物品自动存入邮箱
- 售出商品资金通过邮箱发放
- 下架物品自动退回邮箱
- 交易记录成书详细记录所有交易
- 实时通知系统

### 邮箱配置

```yaml
# 邮箱设置
mailbox:
  # 每页显示物品数量
  items-per-page: 45
  # 交易记录书名
  transaction-record-title: "交易记录"
  # 交易记录作者
  transaction-record-author: "GlobalMarket"
```

### 交易记录成书内容
交易记录成书包含以下信息：
- 出售物品名称
- 出售数量
- 出售价格
- 出售税款
- 实际收入
- 交易时间

### 领取流程
1. 玩家购买物品后，物品和资金进入邮箱
2. 玩家使用 `/market mailbox` 打开邮箱
3. 玩家点击领取物品
4. 系统检查背包空间
5. 如果背包空间充足，发放交易记录成书和物品
6. 资金汇入玩家账户

## 完整配置示例

```yaml
# GlobalMarket 配置文件
# 最大上架物品数量
max-listings-per-player: 10

# 是否启用经济系统支持 (需要Vault插件)
use-economy: true

# 交易税百分比 (0-100)
transaction-tax: 1

# 上架费用百分比 (物品价格的百分比，0为免费)
listing-fee-percentage: 0.5

# 物品过期时间 (小时，0为不过期)
listing-expire-time: 24

# 数据存储类型: yaml, mysql, postgresql
storage-type: yaml

# 是否记录交易日志
log-transactions: true

# GUI设置
gui:
  items-per-page: 45
  enable-animation: true
  auto-refresh: 30

# 邮箱设置
mailbox:
  items-per-page: 45
  transaction-record-title: "交易记录"
  transaction-record-author: "GlobalMarket"

# 消息配置
messages:
  prefix: "&8[&6GlobalMarket&8] &r"
  no-permission: "&c你没有权限执行此操作!"
  item-sold: "&a你的物品已成功出售!"
  item-purchased: "&a你已成功购买物品!"
  market-empty: "&7市场目前没有物品出售。"
  invalid-price: "&c请输入有效的价格!"
  not-enough-money: "&c你没有足够的金币!"
  gui-opened: "&a已打开市场界面!"
  insufficient-funds: "&c余额不足! 需要: %amount%"
  purchase-success: "&a购买成功! 花费: %amount%"
  sale-success: "&a物品上架成功! 价格: %amount%"
  listing-fee-charged: "&a已收取上架费用: %fee% (物品价格的 %percentage%%)"
  mailbox-opened: "&a已打开邮箱!"
  mailbox-empty: "&7邮箱为空。"
  item-received: "&a物品已存入邮箱!"
  money-received: "&a资金已到账: %amount%"
  inventory-full: "&c背包已满! 请先清理背包空间。"
  transaction-record-received: "&a收到交易记录: %item% x%amount% 售价: %price% 税款: %tax% 实际收入: %net%"
```