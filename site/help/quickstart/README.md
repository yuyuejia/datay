# 快速开始

本页帮助你在 10 分钟内把 DataY 跑起来。平台不再预置业务数据，登录后到**应用市场**一键初始化一个业务场景即可。

## 环境要求

- Java 17+
- Maven 3.6+
- Node 22+（构建 DataY Web 前端）

最低配置：**2 核 4G** 的服务器或笔记本即可。

## 一键部署

```bash
# 1. 克隆仓库
git clone https://cnb.cool/yuyuejia/datay

# 2. 构建项目
cd datay
mvn clean package -DskipTests

# 3. 启动（单机模式，调度 + 执行合一）
#    AI 能力需要提供 AI API Key，默认为 Deepseek 原厂 Key
java -jar datay-web/target/*.jar --DATAY_AI_API_KEY=sk-xxxxxxxxx

# 4. 浏览器打开 http://localhost:8080，默认账号 admin/admin
```

在线演示地址：[http://datay-demo.yuyuejia.com.cn/](http://datay-demo.yuyuejia.com.cn/)

## 开发模式

后端与前端分别启动（两个终端），适合二次开发：

```bash
# 终端一：后端
./mvnw

# 终端二：前端
./npmw start
```

- 后端：`http://localhost:8080`
- 前端 Vite 开发服务器：`http://localhost:9000`（已代理到后端，改动自动刷新）

## 生产构建

```bash
cd datay-web
./mvnw -Pprod clean verify
java -jar target/*.jar
```

## 一键初始化：数据应用资产包

平台**不预置任何业务数据**，新租户登录后是干净的。所有演示与业务场景都以「数据应用资产包」的形式
随程序发布，放在 `datay-web/src/main/resources/app-packages/*.json`，启动时自动登记到应用市场。

当前预置两个场景：

| 场景 | 内容 |
| --- | --- |
| **电商销售分析应用**（零售电商） | 业务源库 + 数仓 2 个数据源；6 个维度（时间维度、区域与品类 2 个三级层级维度、门店/商品/客户 3 个普通维度）、DWD 事实表、DWS 汇总、ADS 应用；12 个指标（7 原子 + 5 衍生）；7 个 ETL、3 个 SQL 任务，1 个统一编排任务 |
| **DataY 快速入门应用**（快速入门） | 业务源库 + 数仓 2 个数据源；时间维度 / 层级维度 / 普通维度 / DWD 事实 / ADS 共 5 个模型；4 个 ETL、2 个 SQL 任务，1 个编排任务；适合先跑通「数据源 → ETL → 建模 → 指标 → 汇总」全链路 |

## 第一次登录建议这样走

1. **初始化场景**：进入「数据应用 → 应用市场」，选一个场景点「初始化」，
   一键创建数据源、数据模型、指标、ETL / SQL 任务与编排任务（自动完成 ID 替换）。
2. **跑一次编排**：进入「数据开发 → 任务编排」，找到该场景的编排任务点「执行一次」——
   会先生成业务源库的样例数据，再同步维度与事实表，最后生成汇总表。
3. **看任务实例**：进入「任务实例」，确认每一步都运行成功。
4. **看数据模型**：进入「数据模型 → 维度建模」，查看维度与事实模型（含时间/层级/普通三种维度类型）。
5. **拿一个数**：进入「数据模型 → 智能问数」，重建知识索引后问一句
   「各品类的销售额和毛利率」。

每一步的详细操作见 [使用指南](guide/README.md)，完整实战见
[电商指标问数闭环](cases/ecommerce.md)。

## 常见问题

- **AI 问数不可用**：检查是否配置了 `DATAY_AI_API_KEY`，未配置时助手会自动降级，不影响查询功能。
- **端口冲突**：默认端口 8080，可通过 `--server.port=xxxx` 覆盖。
- **离线环境**：前端构建需要 Node，若只需后端可跳过前端构建。

## 下一步

- [使用指南](guide/README.md)：按功能模块逐个上手
- [场景案例](cases/README.md)：跟着真实场景跑通全链路
- [产品介绍](introduction/README.md)：了解 DataY 的整体设计
