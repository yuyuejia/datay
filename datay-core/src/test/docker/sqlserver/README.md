# SQL Server CDC 本地验证环境

用于 `SqlServerCdcToDuckDBTest`、`SqlServerCdcTypesToDuckDBTest` 和手工验证 SQL Server CDC 组件。

## 快速开始

```bash
cd datay-core/src/test/docker/sqlserver
./start.sh
```

脚本会：

1. 用 Docker Compose 启动 SQL Server 2022，并启用 SQL Server Agent（`MSSQL_AGENT_ENABLED=true`）；
2. 等待 `14333` 端口可连接；
3. 创建数据库 `datay_cdc`；
4. 开启数据库 CDC：`EXEC sys.sp_cdc_enable_db;`；
5. 创建测试账号 `datay / Datay123456!` 并授予 `db_owner`；
6. 创建测试表 `dbo.CDC_DEMO`、`dbo.CDC_TYPES` 并逐表开启 CDC；
7. 启动 CDC 捕获作业并打印校验结果。

## 镜像与平台

默认使用：

```text
mcr.microsoft.com/mssql/server:2022-latest
```

SQL Server 官方 Linux 镜像以 x86_64 为主，Apple Silicon 通过 Docker Desktop 的 amd64 模拟运行。
如使用其他镜像或平台：

```bash
SQLSERVER_IMAGE=mcr.microsoft.com/mssql/server:2019-latest ./start.sh
SQLSERVER_PLATFORM=linux/arm64 ./start.sh
```

端口或密码冲突时：

```bash
SQLSERVER_PORT=14334 SQLSERVER_SA_PASSWORD='AnotherPassw0rd!' ./start.sh
```

## 运行集成测试

```bash
cd /path/to/datay

JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=SqlServerCdcToDuckDBTest
```

自定义连接参数：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=SqlServerCdcToDuckDBTest \
  -Dsqlserver.url='jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc;encrypt=true;trustServerCertificate=true' \
  -Dsqlserver.user=datay \
  -Dsqlserver.password='Datay123456!' \
  -Dsqlserver.schema=dbo
```

常用字段类型覆盖测试：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=SqlServerCdcTypesToDuckDBTest
```

测试不满足前置条件时会自动跳过。

## 常用命令

```bash
# 查看日志
docker logs -f datay-sqlserver-cdc

# 进入 sqlcmd
docker exec -it datay-sqlserver-cdc \
  /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Datay123456!' -C

# 查看 CDC 作业
# EXEC sys.sp_cdc_help_jobs;

# 查看表 CDC 状态
# SELECT s.name, t.name, t.is_tracked_by_cdc
# FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id;

# 停止
docker compose down

# 彻底清理（删除容器与数据卷）
docker compose down -v
```

## 注意事项

- CDC 捕获作业依赖 **SQL Server Agent**，容器已通过 `MSSQL_AGENT_ENABLED=true` 启用；
  SQL Server Express 版没有 Agent，不能用于本测试。
- SQL Server CDC 默认保留 3 天变更，清理作业每天 2 点运行；长时间停机后位点可能失效。
- 测试账号 `datay` 被授予 `db_owner` 仅用于本地验证；生产环境请按组件文档授予最小权限。
- 首次启动 SQL Server 需要初始化系统库，可能需要 30～60 秒；Apple Silicon 的 amd64 模拟会更慢。
- 如果宿主机 `14333` 已被占用，请通过 `SQLSERVER_PORT` 更换端口，并同步修改测试参数。
