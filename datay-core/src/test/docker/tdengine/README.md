# TDengine 验证实例

TDengine 由 Docker 一键启动，用于运行 `TdengineIntegrationTest` 端到端测试。测试覆盖：

- WebSocket 连接（`jdbc:TAOS-WS://127.0.0.1:6041`）
- 数据库 / 表元数据浏览
- 普通表建表与 INSERT 写入
- 超级表写入校验（应给出明确错误，提示改用 `SqlSink`）

## 启动

```bash
cd datay-core/src/test/docker/tdengine
docker compose up -d
```

默认账号：`root` / `taosdata`，端口 `6041`（WebSocket / REST）与 `6030`（原生）。

> 首次启动需要拉取镜像，Apple Silicon 上可能通过 Rosetta 模拟运行。

## 运行测试

测试默认跳过，需要显式开启，并确保本机已下载 `taos-jdbcdriver`（可通过数据源管理页下载，
或允许测试自动从 Maven 仓库下载）：

```bash
cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 21)" \
TDENGINE_IT_URL="jdbc:TAOS-WS://127.0.0.1:6041/" \
mvn -pl datay-core test -Dtest=TdengineIntegrationTest
```

可选环境变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `TDENGINE_IT_URL` | 无（未设置则跳过） | 连接串，建议不指定 database |
| `TDENGINE_IT_USER` | `root` | 用户名 |
| `TDENGINE_IT_PASSWORD` | `taosdata` | 密码 |

## 清理

```bash
cd datay-core/src/test/docker/tdengine
docker compose down -v
```
