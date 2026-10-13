# MongoDB CDC 实时同步到 DuckDB 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将 MongoDB 集合的数据变更（Change Streams）实时同步到内嵌 DuckDB。

- **数据源**：MongoDB（**副本集**，Change Streams 单机不可用）
- **CDC 输入组件**：`MongoCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，写入 DuckDB）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）
- **全量 + 增量**：可开启 `snapshot=true` 首次“先全量、后增量”

> 注意：Change Streams 只捕获组件启动之后的**增量**变更；存量数据可开启 `snapshot=true`，或先用 `MongoDBInput` 做一次全量同步。

## 2. 环境准备

### 2.1 使用 docker 启动单节点副本集

```bash
docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all

docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'

# 确认副本集就绪（ok: 1）
docker exec datay-mongo-cdc mongosh --eval 'rs.status().ok'
```

> 也可直接执行 `datay-core/src/test/docker/mongodb-replica-set/start.sh`；
> 若 27017 已被占用，可用 `MONGO_PORT=27018 MONGO_CONTAINER=datay-mongo-cdc-27018 ./start.sh` 换端口。
>
> 本地验证已预置数据源 **`MongoDB CDC(本地副本集)`**（`mongodb://127.0.0.1:27018`，库 `test`，`directConnection=true`），
> 可在配置界面直接选择。

### 2.2 准备测试集合

```javascript
use test
db.cdc_demo.insertOne({ _id: 1, name: "init" })
```

## 3. 任务配置文件

创建 `mongoCdcToDuckDB.json`：

```json
{
  "units": [
    {
      ".id": "MongoCDCInput",
      ".name": "MongoCDCInput",
      "sourceId": {
        "type": "mongodb",
        "url": "mongodb://127.0.0.1:27017",
        "dbschema": "test",
        "extraParams": { "directConnection": "true" }
      },
      "database": "test",
      "collection": "cdc_demo"
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "url": "jdbc:duckdb:./data/cdc_demo.duckdb",
        "driver": "org.duckdb.DuckDBDriver",
        "dbschema": "main"
      },
      "schema": "main",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    {
      "sourceId": "MongoCDCInput",
      "targetId": "StreamJdbcOutput",
      "sourcePort": 0
    }
  ],
  "version": "1.0.0"
}
```

## 4. 执行 CDC 同步

```bash
mvn clean package -DskipTests

java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar mongoCdcToDuckDB.json
```

## 5. 验证同步效果

在源库执行：

```javascript
db.cdc_demo.insertOne({ _id: 1, name: "alice", amount: 10.5 })
db.cdc_demo.insertOne({ _id: 2, name: "bob",   amount: 20.0 })
db.cdc_demo.updateOne({ _id: 1 }, { $set: { name: "alice2", amount: 11.11 } })
db.cdc_demo.deleteOne({ _id: 2 })
```

目标 DuckDB 会自动建表并同步，最终结果：

```
_id | name   | amount
1   | alice2 | 11.11
```

### 自动化的端到端测试

```bash
# 副本集需运行在 27017（或用 -Dmongo.url 指定）
mvn -pl datay-core test -Dtest=MongoCdcToDuckDBTest -Dmongo.url='mongodb://127.0.0.1:27017'
```

测试覆盖 INSERT / UPDATE / DELETE，以及 `snapshot=true` 的“先全量、后增量”链路。

## 6. 组件参数速查

`MongoCDCInput`：

| 参数 | 必填 | 说明 |
|------|------|------|
| `datasource` | ✅ | MongoDB 连接信息（url/username/password/dbschema/extraParams） |
| `database` / `schema` | ❌ | 数据库名，缺省取数据源 `dbschema` |
| `collection` | ❌ | 采集的单个集合 |
| `collectionNamePattern` | ❌ | 集合名过滤正则（为空采集全部） |
| `fullDocument` | ❌ | 后镜像策略，默认 `updateLookup` |
| `snapshot` | ❌ | 首次运行先全量、后增量，默认 `false` |

`StreamJdbcOutput`（`model=auto`）写入策略：

| 事件 | 写入策略 |
|------|----------|
| INSERT | DuckDB Appender 批量写入 |
| UPDATE | `INSERT OR REPLACE`（按主键 `_id`） |
| DELETE | 按主键 `DELETE` |

## 7. 常见问题

- **报错 `$changeStream is only supported on replica sets`**：使用 `--replSet` 启动并执行 `rs.initiate(...)`。
- **副本集成员 host 不可解析**：`rs.initiate` 时 `host` 要写成采集端可解析的地址（如 `localhost:27017`）。
- **只同步到启动后的变更**：存量数据请开启 `snapshot=true` 或另行全量同步。
- **结构变更不同步**：`drop` / `rename` / `dropDatabase` 只告警，不自动同步。
- **DELETE 只有主键信息**：Change Streams 默认仅提供 `documentKey`，如需完整前镜像需启用 pre-image。

---

通过本案例，您可以快速搭建 MongoDB → DuckDB 的实时 CDC 同步链路。
