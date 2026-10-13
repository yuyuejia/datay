# MongoDB 副本集验证实例

MongoDB CDC（Change Streams）依赖**副本集**，这里提供本地单节点副本集的启动方式，用于运行
`MongoCdcToDuckDBTest` 等端到端测试。

## 方式一：脚本一键启动（推荐）

```bash
cd datay-core/src/test/docker/mongodb-replica-set
./start.sh
```

脚本会创建/启动容器 `datay-mongo-cdc`、等待就绪、初始化副本集 `rs0`（成员地址 `localhost:27017`）。

## 方式二：docker compose

```bash
cd datay-core/src/test/docker/mongodb-replica-set
docker compose up -d

# 首次需要手动初始化副本集
docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
```

## 方式三：手动 docker run

```bash
docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all

docker exec datay-mongo-cdc mongosh --eval \
  'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
```

## 运行测试

```bash
# 确保副本集就绪（rs.status().ok == 1）
docker exec datay-mongo-cdc mongosh --eval 'rs.status().ok'

cd /path/to/datay
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=MongoCdcToDuckDBTest
```

> 测试连接 `mongodb://127.0.0.1:27017`；若实例不可用或非副本集，测试会自动跳过。

## 清理

```bash
docker rm -f datay-mongo-cdc
```
