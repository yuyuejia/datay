# 快速开始

本页帮助你在 10 分钟内把 DataY 跑起来。平台不再预置业务数据，登录后到**应用市场**一键初始化一个业务场景即可。

> 以下命令均在仓库根目录执行；示例使用 macOS / Linux 的 bash、zsh，Windows 请在 PowerShell 或 Git Bash 中执行，并自行调整路径分隔符。

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17、21、24 | 推荐 17；Maven Enforcer 只允许这三个大版本 |
| Maven | 3.6+ | 仓库未内置可用的 Maven Wrapper，请使用系统 `mvn` |
| Node.js | 22.x（默认 22.15.0） | Maven 构建时会自动下载，前端开发时可使用系统 Node |
| npm | 11.x（默认 11.3.0） | 与 Node 一起自动下载 |
| 网络 | 首次构建需要 | 需访问 Maven 中央仓库与 npm 源，用于下载依赖和前端 Node |
| 机器配置 | 2 核 4G 起 | 本地演示足够 |

首次完整构建会下载约 1 GB 依赖，耗时取决于网络；构建完成后再次编译会快很多。

## 一键部署（本地单机，H2）

本地体验推荐使用默认的 `dev` 配置：数据库为内嵌 H2，调度与执行合一，不需要额外安装 MySQL、Redis。

```bash
# 1. 克隆仓库
git clone https://cnb.cool/yuyuejia/datay
cd datay

# 2. 检查环境
java -version
mvn -version

# 3. 完整构建：后端 + 前端静态资源（首次会自动下载 Node 22.15.0 与 npm 依赖）
mvn clean package -DskipTests

# 4. 启动（建议进入 datay-web 启动，工作目录决定 H2、data、log 的位置）
cd datay-web
export DATAY_AI_API_KEY=sk-xxxxxxxxx   # 可不配，未配置时 AI 助手自动降级
java -jar target/datay-web-*.jar

# 5. 验证服务并打开浏览器
curl -s http://localhost:8080/management/health
# 浏览器访问 http://localhost:8080，默认账号 admin/admin
```

构建产物：

| 产物 | 用途 |
| --- | --- |
| `datay-web/target/datay-web-<version>.jar` | 平台主程序，启动这个 JAR |
| `datay-core/target/datay-core-<version>-jar-with-dependencies.jar` | DataY Core 独立运行包，可执行单任务 JSON |

## 编译命令详解

### 默认构建（本地开发 / 演示）

```bash
# 完整构建，跳过测试
mvn clean package -DskipTests

# 增量构建，不清理 target，适合改完代码后快速重打包
mvn package -DskipTests

# 只构建 datay-web 及其依赖的 datay-core
mvn -pl datay-web -am clean package -DskipTests
```

默认会激活 `dev`、`webapp`、`docker-compose` 三个 Profile：

- `dev`：运行时使用 H2 数据库；
- `webapp`：调用前端构建，把静态资源打进 JAR；
- 前端构建使用 `frontend-maven-plugin`，会把 Node 22.15.0 下载到 `datay-web/target/node`，**不需要预先安装 Node**。

可随时确认当前激活的 Profile：

```bash
mvn -pl datay-web help:active-profiles
```

### 只构建后端（不构建前端）

```bash
mvn clean package -DskipTests -Dskip.installnodenpm -Dskip.npm
```

注意：这样打出的 JAR 不包含前端静态资源，直接访问 8080 只能使用接口；本地开发时请配合 Vite 前端（见下文「开发模式」）。

### 生产构建（prod + MySQL）

```bash
mvn -Pprod clean package -DskipTests
```

`-Pprod` 会关闭默认的 `dev` / `webapp` Profile，改用生产配置并构建生产版前端。生产包**默认使用 MySQL**，运行前需准备好数据库，具体启动方式见下文。

### 单独构建 DataY Core

```bash
# 构建并安装到本地 Maven 仓库，供 datay-web 依赖
mvn -pl datay-core -am clean install -DskipTests

# 只打可执行包
mvn -pl datay-core -am clean package -DskipTests
```

### 测试

```bash
# 单元测试
mvn test

# 单元测试 + 集成测试（部分用例依赖 Docker / Testcontainers）
mvn verify
```

## 启动命令详解

### 1. 本地单机启动（H2，推荐）

```bash
cd datay-web
java -jar target/datay-web-*.jar
```

常用启动参数：

```bash
java -jar target/datay-web-*.jar \
  --server.port=8080 \
  --DATAY_AI_API_KEY=sk-xxxxxxxxx \
  --DATAY_AI_BASE_URL=https://api.deepseek.com \
  --DATAY_AI_MODEL=deepseek-flash
```

等价的配置文件位于 `datay-web/src/main/resources/config/application.yml` 的 `datay.ai` 节点；也可以使用环境变量 `DATAY_AI_API_KEY`、`DATAY_AI_BASE_URL`、`DATAY_AI_MODEL`。

后台启动并查看日志：

```bash
cd datay-web
nohup java -jar target/datay-web-*.jar > datay.log 2>&1 &
tail -f datay.log
```

启动成功后，健康检查返回 `{"status":"UP",...}`：

```bash
curl -s http://localhost:8080/management/health
```

### 2. 生产模式启动（MySQL）

先按上文执行 `mvn -Pprod clean package -DskipTests`，再启动：

```bash
cd datay-web

export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/datafusion?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD='change-me'
export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET="$(openssl rand -base64 64 | tr -d '\n')"

java -jar target/datay-web-*.jar
```

说明：

- 数据库不存在时，连接串中的 `createDatabaseIfNotExist=true` 会尝试自动创建；
- 表结构由 Liquibase 自动初始化；
- 生产环境务必替换默认 JWT 密钥与数据库密码；
- `application-prod.yml` 默认连接 `localhost:3306/datafusion`（`root` / 空密码），可用上面的环境变量覆盖。

### 3. 调度 master / worker 分离部署

单机默认即可运行。需要水平扩展时，通过 `development.mode` 指定角色，并配置 Redis：

```bash
export SPRING_PROFILES_ACTIVE=prod
export DEVELOPMENT_MODE=master          # 仅调度；worker 节点改为 worker
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379

java -jar target/datay-web-*.jar
```

详细角色说明与 `docker compose` 示例见[任务调度与实例](guide/scheduler.md)。

## 开发模式

前后端分离启动，适合二次开发。前端 Vite 开发服务器监听 **9000**，并把 `/api`、`/management` 等请求代理到后端 **8080**。

```bash
# 0. 首次开发前，把 datay-core 安装到本地 Maven 仓库
mvn -pl datay-core -am install -DskipTests

# 终端一：启动后端（H2，8080）
cd datay-web
mvn spring-boot:run -Dskip.installnodenpm -Dskip.npm

# 终端二：启动前端（9000，支持热更新）
cd datay-web
npm install        # 首次执行；已有 node_modules 可跳过
npm start
```

- 后端：`http://localhost:8080`
- 前端：`http://localhost:9000`（页面从这里打开，接口自动代理到后端）
- 前端 Node 版本建议 22.x；也可以直接使用 Maven 已下载的版本：`export PATH="$PWD/target/node:$PATH"`

## 数据目录与重置

从 `datay-web` 目录启动时，运行数据都相对于该目录：

| 路径 | 内容 |
| --- | --- |
| `datay-web/target/h2db/db/datafusion` | 本地 H2 数据库（账号、租户、任务等） |
| `datay-web/data` | DuckDB、DuckLake、智能问数索引、上传文件等 |
| `datay-web/log` | 任务运行日志 |
| `datay-web/drivers` | 外部 JDBC 驱动 |

> `mvn clean` 会删除整个 `target`，包括本地 H2 数据库。需要保留演示数据时，请先备份
> `datay-web/target/h2db`，或改用 MySQL 作为元数据库。
>
> 重置本地环境：停止服务后删除 `datay-web/target/h2db`，重新启动即可得到全新平台。

## 一键初始化：数据应用资产包

平台**不预置任何业务数据**，新租户登录后是干净的。所有演示与业务场景都以「数据应用资产包」的形式
随程序发布，放在 `datay-web/src/main/resources/app-packages/*.json`，启动时自动登记到应用市场。

当前预置两个场景：

| 场景 | 内容 |
| --- | --- |
| **电商销售分析应用**（零售电商） | 业务源库 + 数仓 2 个数据源；6 个维度（时间维度、区域与品类 2 个三级层级维度、门店/商品/客户 3 个普通维度）、DWD 事实表、DWS 汇总、ADS 应用；12 个指标（7 原子 + 5 衍生）；7 个 ETL、3 个 SQL 任务，1 个统一编排任务 |
| **DataY 快速入门应用**（快速入门） | 业务源库 + 数仓 2 个开箱即用的 DuckDB 数据源，另含可绑定的 MySQL / PostgreSQL CDC 演示数据源与 1 个本地 DuckLake 数据源；时间维度 / 层级维度 / 普通维度 / DWD 事实 / ADS 共 5 个模型；12 个 ETL（4 个同步 + 属性路由分流 + 清洗转换 / 多表关联 / SQL 聚合 / 增量同步 + MySQL Binlog CDC 实时同步 + PostgreSQL CDC 实时入湖 + 决策模型工单分派）、2 个 SQL 任务，1 个编排任务，并预置 4 个指标、1 个 SQL 分析数据集、1 个标准分析看板和 1 个 1920×1080 暗色大屏看板；适合先跑通「数据源 → ETL → 建模 → 指标 → 汇总 → 分析看板」全链路 |

> 快速入门应用中的 **MySQL Binlog CDC**、**PostgreSQL CDC 入湖** 与 **决策模型** 三个演示 ETL 任务独立运行，未纳入每日编排：
> MySQL CDC 演示需要本地 MySQL 开启 `binlog_format=ROW` 并具备复制权限（可在初始化时将「入门MySQL(CDC演示)」绑定到已有数据源）；
> PostgreSQL CDC 入湖演示需要本地 PostgreSQL 开启 `wal_level=logical`、账号具备 `REPLICATION` 权限，且源表设置 `REPLICA IDENTITY FULL`（可绑定「入门PostgreSQL(CDC演示)」）；
> 决策模型演示需要先到「服务配置 · 决策模型」连通 Ollama（默认 `http://localhost:11434/v1`，模型 `nimble`）。

## 第一次登录建议这样走

1. **初始化场景**：进入「数据应用 → 应用市场」，选一个场景点「初始化」，
   一键创建数据源、数据模型、指标、分析数据集、分析看板、ETL / SQL 任务与编排任务（自动完成 ID 替换）。
2. **跑一次编排**：进入「数据开发 → 任务编排」，找到该场景的编排任务点「执行一次」——
   会先生成业务源库的样例数据，再同步维度与事实表，最后生成汇总表。
3. **看任务实例**：进入「任务实例」，确认每一步都运行成功。
4. **看数据模型**：进入「数据模型 → 维度建模」，查看维度与事实模型（含时间/层级/普通三种维度类型）。
5. **拿一个数**：进入「数据模型 → 智能问数」，重建知识索引后问一句
   「各品类的销售额和毛利率」。
6. **打开分析看板**：进入「分析看板」，打开快速入门预置的「入门门店销售分析看板」；
   它通过 SQL 分析数据集「入门门店月度销售数据集」读取 ADS 汇总表，可直接切换门店筛选器查看销售额、订单数、月度趋势与门店占比。
7. **（可选）跑进阶演示**：在「数据开发 → ETL 任务」中找到快速入门应用的
   「MySQL Binlog CDC 实时同步演示」「PostgreSQL CDC 实时入湖演示」与「决策模型工单分派演示」，
   补齐 MySQL / PostgreSQL / Ollama 环境后单跑，分别体验实时变更捕获、CDC 入湖与本地决策模型算子。

每一步的详细操作见 [使用指南](guide/README.md)，完整实战见
[电商指标问数闭环](cases/ecommerce.md)。

## 常见问题

- **提示 Java 版本不兼容**：Maven Enforcer 只接受 JDK 17、21、24。执行 `java -version`、`mvn -version` 确认两者使用同一个 JDK，必要时设置 `JAVA_HOME`。
- **`./mvnw: No such file or directory` 或 Wrapper 报错**：仓库未包含可用的 Maven Wrapper，请直接使用系统 `mvn`。
- **`./npmw: Permission denied`**：`npmw` 默认没有执行权限，可执行 `chmod +x npmw`，或直接使用 `npm install && npm start`。
- **前端构建失败或长时间卡住**：多为下载 Node / npm 依赖失败。可先只构建后端：`mvn clean package -DskipTests -Dskip.installnodenpm -Dskip.npm`，此时页面资源需另用 Vite 启动。
- **启动报数据库连接失败**：确认没有误用 `prod` Profile。本地体验请使用默认包直接启动（H2）；生产模式必须先准备 MySQL。
- **AI 问数不可用**：检查是否配置了 `DATAY_AI_API_KEY`，未配置时助手会自动降级，不影响查询功能。
- **端口冲突**：默认端口 8080，可通过 `--server.port=xxxx` 覆盖；前端开发端口 9000 可在 `datay-web/vite.config.mts` 中修改。
- **本地数据丢失**：`mvn clean` 会删除 `datay-web/target/h2db`，请先备份或改用 MySQL。

## 下一步

- [使用指南](guide/README.md)：按功能模块逐个上手
- [场景案例](cases/README.md)：跟着真实场景跑通全链路
- [产品介绍](introduction/README.md)：了解 DataY 的整体设计
