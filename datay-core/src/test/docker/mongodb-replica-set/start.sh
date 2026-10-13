#!/usr/bin/env bash
#
# 启动本地单节点 MongoDB 副本集，用于 MongoDB CDC 端到端验证。
# 用法：./start.sh
#
set -euo pipefail

CONTAINER_NAME="${MONGO_CONTAINER:-datay-mongo-cdc}"
IMAGE="${MONGO_IMAGE:-mongo:7}"
PORT="${MONGO_PORT:-27017}"
REPL_SET="${MONGO_REPL_SET:-rs0}"

if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "容器 ${CONTAINER_NAME} 已存在，直接启动"
  docker start "${CONTAINER_NAME}" >/dev/null
else
  echo "创建并启动容器 ${CONTAINER_NAME}"
  docker run -d --name "${CONTAINER_NAME}" -p "${PORT}:${PORT}" "${IMAGE}" \
    --replSet "${REPL_SET}" --bind_ip_all --port "${PORT}" >/dev/null
fi

echo "等待 mongod 就绪..."
for i in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" mongosh --quiet --port "${PORT}" --eval "db.adminCommand('ping').ok" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo "初始化副本集 ${REPL_SET}（host=localhost:${PORT}）..."
docker exec "${CONTAINER_NAME}" mongosh --quiet --port ${PORT} --eval \
  "try { rs.status() } catch (e) { rs.initiate({_id:'${REPL_SET}',members:[{_id:0,host:'localhost:${PORT}'}]}) }" >/dev/null

echo "等待副本集成为 PRIMARY..."
for i in $(seq 1 30); do
  if docker exec "${CONTAINER_NAME}" mongosh --quiet --port "${PORT}" --eval "db.hello().isWritablePrimary" 2>/dev/null | grep -q true; then
    echo "MongoDB 副本集就绪：mongodb://127.0.0.1:${PORT}"
    exit 0
  fi
  sleep 1
done

echo "副本集初始化超时，请查看日志：docker logs ${CONTAINER_NAME}" >&2
exit 1
