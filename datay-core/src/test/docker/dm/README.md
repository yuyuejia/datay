# 达梦 8 本地验证环境

用于 `DmCdcToDuckDBTest` 和手工验证达梦 CDC 组件。

## 快速开始

```bash
cd datay-core/src/test/docker/dm
./start.sh
```

脚本会：

1. 用 Docker Compose 启动达梦 8；
2. 等待 `5236` 端口可连接；
3. 开启归档、`RLOG_APPEND_LOGIC=2`、`RLOG_IGNORE_TABLE_SET=1`；
4. 重启达梦实例使 `RLOG_APPEND_LOGIC` 生效；
5. 创建 `DBMS_LOGMNR` 系统包；
6. 创建测试用户 `DATAY / Datay123456` 并授予 DBA；
7. 校验 `ARCH_MODE` 等关键参数。

## 镜像说明

`start.sh` 会优先使用本机已有的达梦镜像，按以下顺序探测：

1. `forresttse/dm8:latest`
2. `docker.jiaxin.site/danilaworker/damengdb:20250206`
3. `dm8:dm8_20250206_rev257733_x86_rh6_64`

找不到时可通过环境变量指定镜像：

```bash
# 使用自己导入的官方镜像
DM_IMAGE=dm8:dm8_20250206_rev257733_x86_rh6_64 ./start.sh

# 使用 arm64 镜像
DM_PLATFORM=linux/arm64 DM_IMAGE=<arm64-image> ./start.sh
```

也可以从达梦官网下载 Docker 安装包后导入：

```bash
docker load -i dm8_20250206_x86_rh6_64_rq_single.tar
DM_IMAGE=dm8:dm8_20250206_rev257733_x86_rh6_64 ./start.sh
```

如果本机已经存在名为 `datay-dm8` 的容器，`start.sh` 会直接复用它，不会重新创建。

## 运行集成测试

```bash
cd /path/to/datay

JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=DmCdcToDuckDBTest
```

自定义连接参数：

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=DmCdcToDuckDBTest \
  -Ddm.url=jdbc:dm://127.0.0.1:5236 \
  -Ddm.user=DATAY \
  -Ddm.password=Datay123456 \
  -Ddm.schema=DATAY
```

测试不满足前置条件时会自动跳过。

## 常用命令

```bash
# 查看日志
docker logs -f datay-dm8

# 进入 disql
docker exec -it datay-dm8 bash
/opt/dmdbms/bin/disql SYSDBA/SYSDBA001@localhost:5236

# 手工触发归档切换
ALTER SYSTEM ARCHIVE LOG CURRENT;

# 停止
docker compose down

# 彻底清理（删除数据与归档）
docker compose down
rm -rf data arch
```

## 注意事项

- `PAGE_SIZE`、`EXTENT_SIZE`、`CASE_SENSITIVE`、`UNICODE_FLAG` 等初始化参数在实例创建后不可修改；切换镜像或修改参数时需删除 `data/` 重新初始化。
- 达梦 `DBMS_LOGMNR` 只支持本地归档日志，`ARCH_DEST` 必须是数据库实例本机可访问的目录。
- 测试用户 `DATAY` 被授予 `DBA` 仅用于本地验证；生产环境请按组件文档授予最小权限。
- `ALTER SYSTEM ARCHIVE LOG CURRENT` 会产生新的归档文件，测试完成后可按需清理 `arch/`。
