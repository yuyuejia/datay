# 快速开始

本页帮助你在 10 分钟内把 DataY 跑起来。平台启动时会自动预置一整套电商示例，**登录就能看到**。

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

## 开箱即用：预置的电商资产

平台启动时自动通过数据库迁移脚本预置了一整套电商示例：

| 类型 | 预置内容 |
| --- | --- |
| 数据源 | `电商数仓(DuckDB)` |
| 维度表 | `dim_date`、`dim_customer`、`dim_store`、`dim_product`、`dim_brand`、`dim_category`（3 级层级）、`dim_time`（年/季/月/日） |
| 事实表 | `fact_sales_order`、`fact_sales_order_item`、`fact_customer_member` |
| 指标 | 销售额、销量、成本、毛利、毛利率、客单价等 12 个 |
| 同步任务 | `EC_DIM_*` / `EC_FACT_*` 系列，文件 → 模型 |

![首页资产概览](../images/首页资产概览.png)

## 第一次登录建议这样走

1. **看数据源**：进入「数据源 → 数据源管理」，查看预置的 `电商数仓(DuckDB)`。
2. **跑数据集成**：进入「数据集成」，找到 `EC_DIM_*` / `EC_FACT_*` 任务，点「执行一次」。
3. **看任务实例**：进入「任务实例」，确认任务运行成功。
4. **看数据模型**：进入「数据模型 → 维度建模」，查看预置的维度与事实模型。
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
