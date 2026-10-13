# Oracle LogMiner 验证实例

Oracle CDC（LogMiner）依赖**归档模式 + 补充日志 + LOGMINING 权限**，这里提供本地 Oracle Free 容器的启动与配置方式，用于运行 `OracleCdcToDuckDBTest` 端到端测试。

镜像使用 `gvenzl/oracle-free:23-slim`（支持 linux/amd64 与 linux/arm64，Apple Silicon 可直接运行）。

## 一键启动

```bash
cd datay-core/src/test/docker/oracle
./setup.sh
```

脚本会：

1. 通过 `docker-compose.yml` 启动容器 `datay-oracle-cdc`（端口 `1521`），首次启动初始化数据库较慢（数分钟）；
2. 等待容器健康检查通过；
3. 在 CDB（`FREE`）开启归档模式 `ARCHIVELOG` 和最小 / 全列补充日志；
4. 在 PDB（`FREEPDB1`）开启补充日志，并给 `datay` 账号授予 `LOGMINING` 等权限。

## 连接信息

| 项 | 值 |
|----|----|
| 连接串 | `jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1` |
| 应用账号 | `datay` / `datay123`（schema `DATAY`）|
| SYSDBA | `sys` / `oracle` |

## 手动启动（可选）

```bash
cd datay-core/src/test/docker/oracle
docker compose up -d

# 等待 healthy 后执行 setup.sh 中第 3、4 步的 SQL（或用 setup.sh 一键完成）
```

## 运行测试

```bash
cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=OracleCdcToDuckDBTest
```

> 测试连接 `127.0.0.1:1521/FREEPDB1`（可用 `-Doracle.url= -Doracle.user= -Doracle.password=` 覆盖）；
> 若实例不可用、未开启归档/补充日志或权限不足，测试会自动跳过。

## 清理

```bash
cd datay-core/src/test/docker/oracle
docker compose down -v
```
