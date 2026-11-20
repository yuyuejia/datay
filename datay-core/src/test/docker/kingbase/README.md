# 人大金仓 KingbaseES 验证实例

KingbaseES 由 Docker 启动，用于运行 `KingbaseIntegrationTest` 端到端测试。测试覆盖：

- 连接与连通性测试（`jdbc:kingbase8://127.0.0.1:54321/test`）
- schema / 表 / 字段元数据浏览，主键、精度、注释、索引回读
- 自动建表（PostgreSQL 兼容方言）与 `COMMENT ON`
- INSERT、`ON CONFLICT` UPSERT、参数化查询、DELETE
- `StreamJdbcOutput` ETL 写入：append / update / delete，目标表不存在时自动建表
- SMALLINT / BIGINT / NUMERIC / DOUBLE / VARCHAR / TEXT / BOOLEAN / DATE / TIMESTAMP 类型写入回读

## 准备 license

社区镜像自带的试用 license 通常已过期，需要准备一份有效的 V8R3 license，并放到本目录：

```bash
cp /path/to/license.dat datay-core/src/test/docker/kingbase/license.dat
```

也可以通过环境变量指定其他路径：

```bash
KINGBASE_LICENSE_FILE=/path/to/license.dat docker compose up -d
```

## 启动

```bash
cd datay-core/src/test/docker/kingbase
docker compose up -d
```

默认账号：`system` / `Kingbase123`，端口 `54321`，默认数据库 `test`。

> Apple Silicon 上可能通过 Rosetta 模拟运行 amd64 镜像。

如果 license 对连接数有限制（例如仅允许 10 个连接），需要同步调整数据库参数，否则实例会启动失败：

```bash
docker exec datay-kingbase-r3 sh -c \
  "sed -i -E 's/^max_connections.*/max_connections = 10/' /opt/kingbase/data/kingbase.conf"
docker exec datay-kingbase-r3 sh -c \
  "su kingbase -c '/opt/kingbase/Server/bin/sys_ctl restart -D /opt/kingbase/data'"
```

## 驱动

驱动不随平台预置，需要放到 `drivers/kingbase/default/`。**注意版本必须与服务端匹配**：

| 服务端版本 | 推荐驱动 |
|------------|----------|
| V8R3 | `kingbase8-8.2.0-V008R003C002B0100.jar`（8.2.x） |
| V8R6 | `kingbase8-8.6.0.jar`（8.6.x） |
| V9R1 | `kingbase8-9.0.1.jar`（9.0.x） |

V8R3 使用 8.6.x 驱动时会访问新版本系统视图，常见报错：

```text
relation "PG_CATALOG.PG_namespace" does not exist
```

因此 V8R3 必须使用 8.2.x 驱动。测试前请确认 `datay-core/drivers/kingbase/default/` 下只有与当前实例匹配的驱动 jar。

## 运行测试

```bash
cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
KINGBASE_IT_URL="jdbc:kingbase8://127.0.0.1:54321/test" \
KINGBASE_IT_USER="system" \
KINGBASE_IT_PASSWORD="Kingbase123" \
mvn -pl datay-core test -Dtest=KingbaseIntegrationTest
```

可选环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `KINGBASE_IT_URL` | 无（未设置则跳过） | 连接串 |
| `KINGBASE_IT_USER` | `system` | 用户名 |
| `KINGBASE_IT_PASSWORD` | `Kingbase123` | 密码 |

## 说明

- V8R3 在 `--case-insensitive` 模式下默认 schema 在元数据中显示为 `PUBLIC`，SQL 中写 `public` 或 `PUBLIC` 均可访问；集成测试按实际元数据值选择 schema。
- 连接池默认大小可能超过试用 license 的连接数上限，测试用例按顺序执行即可通过。
- CDC 未包含在本次验证范围内，仅验证 JDBC 批量 / 增量同步相关能力。

## 清理

```bash
cd datay-core/src/test/docker/kingbase
docker compose down -v
```
