# 构建说明

## 方法一：使用Maven构建（推荐）

### 安装Maven
1. 访问 https://maven.apache.org/download.cgi
2. 下载Maven 3.9.x版本
3. 解压到任意目录
4. 将Maven的bin目录添加到系统PATH环境变量
5. 验证安装：打开新的命令提示符，运行 `mvn -version`

### 构建命令
```bash
cd e:\GlobalMarket
mvn clean package
```

构建完成后，插件jar文件将位于：
`target/GlobalMarket-1.0.0.jar`

## 方法二：手动构建

### 步骤1：编译Java代码
```bash
# 确保你有Java 21 JDK
javac -cp "path/to/paper-1.21.8-api.jar" -d target/classes src/main/java/com/globalmarket/*.java
```

### 步骤2：复制资源文件
```bash
# 复制配置文件和plugin.yml
copy src\main\resources\* target\classes\
```

### 步骤3：创建jar文件
```bash
# 进入classes目录
cd target\classes

# 创建jar文件
jar -cvf GlobalMarket-1.0.0.jar .
```

## 方法三：使用IDE构建

### IntelliJ IDEA
1. 打开项目
2. 等待Maven依赖下载
3. 点击右侧Maven面板 → Lifecycle → package
4. 或使用快捷键：Ctrl+F9

### Eclipse
1. 导入Maven项目
2. 右键项目 → Run As → Maven build...
3. 在Goals中输入：clean package
4. 点击Run

## 安装插件

1. 将构建好的 `GlobalMarket-1.0.0.jar` 复制到服务器的 `plugins/` 目录
2. 启动或重启Minecraft服务器
3. 插件将自动创建配置文件

## 验证安装

1. 启动服务器后查看控制台输出
2. 应该看到 "GlobalMarket 插件已启用!" 的消息
3. 使用 `/market listing` 命令测试功能
4. 使用 `/market search diamond` 测试搜索功能
5. 使用 `/market mailbox` 测试邮箱系统