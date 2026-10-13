#!/usr/bin/env bash
#
# 启动 Oracle Free 容器（若未运行），并完成 LogMiner 采集所需的环境配置：
#   1. 开启归档模式（ARCHIVELOG）；
#   2. 开启最小 / 全列补充日志（SUPPLEMENTAL LOG DATA (ALL) COLUMNS）；
#   3. 授予 datay 账号 LOGMINING 角色与 DBMS_LOGMNR 权限。
#
# 依赖：docker、docker compose
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTAINER="datay-oracle-cdc"

echo "==> 启动 Oracle 容器（首次启动初始化较慢，请耐心等待）"
docker compose -f "${SCRIPT_DIR}/docker-compose.yml" up -d

echo "==> 等待容器健康检查通过"
for _ in $(seq 1 120); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || echo unknown)"
  if [ "${status}" = "healthy" ]; then
    break
  fi
  sleep 10
done
status="$(docker inspect -f '{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || echo unknown)"
if [ "${status}" != "healthy" ]; then
  echo "容器未能在预期时间内变为 healthy（当前：${status}）" >&2
  exit 1
fi

echo "==> 在 CDB（FREE）开启归档模式与补充日志"
docker exec -i "${CONTAINER}" bash -c 'sqlplus -S / as sysdba' <<'SQL'
WHENEVER SQLERROR EXIT SQL.SQLCODE
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
ALTER DATABASE ARCHIVELOG;
ALTER DATABASE OPEN;
ALTER PLUGGABLE DATABASE ALL OPEN;
ALTER PLUGGABLE DATABASE ALL SAVE STATE;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
EXIT;
SQL

echo "==> 在 PDB（FREEPDB1）开启补充日志并授权 datay"
docker exec -i "${CONTAINER}" bash -c 'sqlplus -S / as sysdba' <<'SQL'
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA;
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
GRANT LOGMINING TO datay;
GRANT SELECT ANY TRANSACTION TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR TO datay;
GRANT EXECUTE ON SYS.DBMS_LOGMNR_D TO datay;
GRANT SELECT ON SYS.V_$LOGMNR_CONTENTS TO datay;
GRANT SELECT ON SYS.V_$LOGMNR_LOGS TO datay;
GRANT SELECT ON SYS.V_$ARCHIVED_LOG TO datay;
GRANT SELECT ON SYS.V_$LOGFILE TO datay;
GRANT SELECT ON SYS.V_$LOG TO datay;
GRANT SELECT ON SYS.V_$DATABASE TO datay;
GRANT RESOURCE, CONNECT, UNLIMITED TABLESPACE TO datay;
EXIT;
SQL

echo "==> Oracle LogMiner 测试环境就绪"
echo "    连接串：jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1"
echo "    账号：datay / datay123（schema: DATAY）"
