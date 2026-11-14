#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

DM_PORT="${DM_PORT:-5236}"
CONTAINER="${DM_CONTAINER:-datay-dm8}"

# 优先使用本机已有镜像，避免每次联网拉取
if [ -z "${DM_IMAGE:-}" ]; then
  for candidate in \
    "forresttse/dm8:latest" \
    "docker.jiaxin.site/danilaworker/damengdb:20250206" \
    "dm8:dm8_20250206_rev257733_x86_rh6_64"; do
    if docker image inspect "$candidate" >/dev/null 2>&1; then
      DM_IMAGE="$candidate"
      break
    fi
  done
fi
if [ -z "${DM_IMAGE:-}" ]; then
  echo "未找到本地达梦镜像，请先下载/导入镜像后设置 DM_IMAGE，例如：" >&2
  echo "  DM_IMAGE=dm8:dm8_20250206_rev257733_x86_rh6_64 ./start.sh" >&2
  exit 1
fi
export DM_IMAGE

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "==> 复用已存在的达梦容器 $CONTAINER"
  if [ -z "${DM_SYSDBA_PASSWORD:-}" ]; then
    DM_SYSDBA_PASSWORD="$(
      docker inspect "$CONTAINER" --format '{{range .Config.Env}}{{println .}}{{end}}' \
        | sed -n 's/^SYSDBA_PWD=//p' | head -n 1
    )"
  fi
  docker start "$CONTAINER" >/dev/null 2>&1 || true
else
  echo "==> 启动达梦容器（镜像: ${DM_IMAGE}，端口: ${DM_PORT}）"
  docker compose up -d
fi
DM_SYSDBA_PASSWORD="${DM_SYSDBA_PASSWORD:-SYSDBA001}"
export DM_SYSDBA_PASSWORD

echo "==> 等待达梦数据库就绪"
wait_ready() {
  for _ in $(seq 1 120); do
    if docker exec "$CONTAINER" bash -lc \
      "echo 'select 1 from dual;' | /opt/dmdbms/bin/disql SYSDBA/${DM_SYSDBA_PASSWORD}@localhost:5236" \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

if ! wait_ready; then
  echo "达梦数据库启动超时，请查看：docker logs -f $CONTAINER" >&2
  exit 1
fi

echo "==> 清理旧测试用户（不存在时忽略）"
docker exec -i "$CONTAINER" bash -lc \
  "/opt/dmdbms/bin/disql SYSDBA/${DM_SYSDBA_PASSWORD}@localhost:5236" \
  < drop_user.sql >/dev/null 2>&1 || true

echo "==> 初始化归档、逻辑日志、DBMS_LOGMNR 与测试用户"
docker exec -i "$CONTAINER" bash -lc \
  "/opt/dmdbms/bin/disql SYSDBA/${DM_SYSDBA_PASSWORD}@localhost:5236" \
  < setup.sql

# RLOG_APPEND_LOGIC 修改后需要重启实例才会按新级别记录逻辑日志
echo "==> 重启达梦实例使 RLOG_APPEND_LOGIC 生效"
docker restart "$CONTAINER" >/dev/null
if ! wait_ready; then
  echo "达梦数据库重启后未就绪，请查看：docker logs -f $CONTAINER" >&2
  exit 1
fi

echo "==> 校验关键参数"
docker exec -i "$CONTAINER" bash -lc \
  "/opt/dmdbms/bin/disql SYSDBA/${DM_SYSDBA_PASSWORD}@localhost:5236" <<'SQL'
SELECT ARCH_MODE FROM V$DATABASE;
SELECT PARA_NAME, PARA_VALUE FROM V$DM_INI
 WHERE PARA_NAME IN ('ARCH_INI','RLOG_APPEND_LOGIC','RLOG_IGNORE_TABLE_SET','LOGMNR_DELAY_ANALYZE');
SQL

cat <<EOF

达梦 CDC 测试环境已就绪：
  URL      : jdbc:dm://127.0.0.1:${DM_PORT}
  用户     : DATAY / Datay123456
  Schema   : DATAY
  SYSDBA   : SYSDBA / ${DM_SYSDBA_PASSWORD}

运行集成测试：
  cd ../../../../..
  JAVA_HOME="\$(/usr/libexec/java_home -v 17)" \\
    mvn -pl datay-core test -Dtest=DmCdcToDuckDBTest \\
    -Ddm.url=jdbc:dm://127.0.0.1:${DM_PORT}

停止环境：
  docker compose down
  # 同时删除数据目录：docker compose down && rm -rf data arch

EOF
