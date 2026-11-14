#!/usr/bin/env bash
#
# 启动 SQL Server 2022 本地验证环境并初始化 CDC。
#
# 使用方式：
#   cd datay-core/src/test/docker/sqlserver
#   ./start.sh
#
# 可通过环境变量覆盖默认值：
#   SQLSERVER_CONTAINER / SQLSERVER_PORT / SQLSERVER_SA_PASSWORD / SQLSERVER_IMAGE / SQLSERVER_PLATFORM
#
set -euo pipefail

cd "$(dirname "$0")"

CONTAINER="${SQLSERVER_CONTAINER:-datay-sqlserver-cdc}"
PORT="${SQLSERVER_PORT:-14333}"
SA_PASSWORD="${SQLSERVER_SA_PASSWORD:-Datay123456!}"

echo "==> 启动 SQL Server 容器：${CONTAINER}，端口：${PORT}"
docker compose up -d

echo "==> 等待 SQL Server 就绪"
for i in $(seq 1 90); do
    if docker exec "$CONTAINER" bash -lc "/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P '${SA_PASSWORD}' -C -Q 'SELECT 1' >/dev/null 2>&1" 2>/dev/null; then
        break
    fi
    if docker exec "$CONTAINER" bash -lc "/opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P '${SA_PASSWORD}' -Q 'SELECT 1' >/dev/null 2>&1" 2>/dev/null; then
        break
    fi
    if [ "$i" -eq 90 ]; then
        echo "SQL Server 在 180 秒内未就绪，最近日志：" >&2
        docker logs --tail 80 "$CONTAINER" >&2 || true
        exit 1
    fi
    sleep 2
done

SQLCMD="/opt/mssql-tools18/bin/sqlcmd"
SQLCMD_EXTRA="-C"
if ! docker exec "$CONTAINER" test -x "$SQLCMD"; then
    SQLCMD="/opt/mssql-tools/bin/sqlcmd"
    SQLCMD_EXTRA=""
fi

echo "==> 执行 setup.sql（创建数据库、账号、测试表并开启 CDC）"
# shellcheck disable=SC2086
if ! docker exec -i "$CONTAINER" "$SQLCMD" -S localhost -U sa -P "$SA_PASSWORD" $SQLCMD_EXTRA -b < setup.sql; then
    echo "初始化失败，最近日志：" >&2
    docker logs --tail 100 "$CONTAINER" >&2 || true
    exit 1
fi

echo
echo "==> 初始化完成"
echo "    容器：${CONTAINER}"
echo "    地址：127.0.0.1:${PORT}"
echo "    数据库：datay_cdc"
echo "    测试账号：datay / Datay123456!"
echo "    测试表：dbo.CDC_DEMO、dbo.CDC_TYPES"
echo
echo "运行集成测试："
echo "  cd /path/to/datay"
echo "  JAVA_HOME=\"\$(/usr/libexec/java_home -v 17)\" \\"
echo "    mvn -pl datay-core test -Dtest=SqlServerCdcToDuckDBTest"
echo
echo "常用命令："
echo "  docker logs -f ${CONTAINER}"
echo "  docker exec -it ${CONTAINER} ${SQLCMD} -S localhost -U sa -P '${SA_PASSWORD}' -C"
echo "  docker compose down"
