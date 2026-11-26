# 达梦 CDC 实时同步到 Apache Doris 案例文档

## 1. 场景说明

本案例演示如何使用 DataY 将达梦 8 的数据变更（CDC）实时同步到 Apache Doris。

- **数据源**：达梦 8（`DBMS_LOGMNR` 归档日志挖掘）
- **CDC 输入组件**：`DMCDCInput`
- **输出组件**：`StreamJdbcOutput`（`model=auto`，通过 MySQL 协议写入 Doris）
- **同步模式**：按事件类型自动选择写入策略（INSERT / UPDATE / DELETE）

> Doris 侧目标表由 DataY 按源表主键自动创建为 **UNIQUE KEY** 模型。
> 该模型对相同主键执行整行 INSERT 即为 UPSERT，因此 UPDATE 事件直接复用 INSERT 语句；
> 注意 Doris 2.x 不支持 MySQL 的 `ON DUPLICATE KEY UPDATE` 语法。

## 2. 环境准备

### 2.1 启动达梦验证实例

```bash
cd datay-core/src/test/docker/dm
./start.sh
```

脚本会自动开启归档、逻辑日志，创建 `DBMS_LOGMNR` 系统包，并创建测试用户
`DATAY / Datay123456`。详见该目录下的 `README.md`。

### 2.2 启动 Doris 验证实例

可任选官方 all-in-one 镜像，将 FE 查询端口（9030）与 HTTP 端口（8030）映射到本机：

```bash
docker run -d --name datay-doris \
  -p 8030:8030 -p 8040:8040 -p 9030:9030 \
  docker.m.daocloud.io/apache/doris:doris-all-in-one-2.1.0

# 等待 FE 就绪（返回 1 即表示可连接）
docker exec datay-doris mysql -h127.0.0.1 -P9030 -uroot -e "select 1"
```

> 若本机已有可用的 Doris（默认 `127.0.0.1:9030`，root 无密码），可直接复用。

### 2.3 创建测试表

```sql
CREATE TABLE DATAY.CDC_DM_DORIS (
    ID          INT PRIMARY KEY,
    NAME        VARCHAR(100),
    AMOUNT      DECIMAL(12,2),
    UPDATED_AT  TIMESTAMP
);
```

## 3. 任务配置文件

`datay-core/src/test/dmCdcToDoris.json` 已内置可直接运行的完整示例，核心配置：

```json
{
  "units": [
    {
      ".id": "DMCDCInput",
      ".name": "DMCDCInput",
      "sourceId": {
        "type": "dm",
        "url": "jdbc:dm://127.0.0.1:5236",
        "driver": "dm.jdbc.driver.DmDriver",
        "username": "DATAY",
        "password": "Datay123456",
        "dbschema": "DATAY"
      },
      "schema": "DATAY",
      "tableNamePattern": "CDC_DM_DORIS",
      "snapshot": true,
      "archiveSwitch": true,
      "archiveSwitchIntervalMs": 1000,
      "pollIntervalMs": 500
    },
    {
      ".id": "StreamJdbcOutput",
      ".name": "StreamJdbcOutput",
      "sourceId": {
        "type": "doris",
        "url": "jdbc:mysql://127.0.0.1:9030/datay_dm_cdc?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "driver": "com.mysql.cj.jdbc.Driver",
        "username": "root",
        "password": "",
        "dbschema": "datay_dm_cdc"
      },
      "schema": "datay_dm_cdc",
      "table": "",
      "model": "auto"
    }
  ],
  "connections": [
    { "sourceId": "DMCDCInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
  ],
  "version": "1.0.0"
}
```

## 4. 执行 CDC 同步

```bash
# 构建（跳过测试）
mvn clean package -DskipTests

# 运行（datay-core 可执行包）
java -jar datay-core/target/datay-core-1.0.0-SNAPSHOT-jar-with-dependencies.jar dmCdcToDoris.json
```

在达梦端执行 `INSERT / UPDATE / DELETE` 后，可在 Doris 中观察变更：

```sql
SELECT * FROM datay_dm_cdc.cdc_dm_doris ORDER BY id;
SHOW CREATE TABLE datay_dm_cdc.cdc_dm_doris;   -- 应为 UNIQUE KEY(`id`)
```

## 5. 端到端自动化验证

```bash
# 先启动达梦与 Doris（见 2.1 / 2.2）
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  mvn -pl datay-core test -Dtest=DmCdcToDorisTest
```

`DmCdcToDorisTest` 覆盖：

- 快照同步（启动前已存在的基线数据）；
- INSERT 常用字段类型（整数、定点/浮点、字符、日期时间、BIT、中文）；
- UPDATE 全字段覆盖写入；
- UPDATE 主键（旧主键删除、新主键写入）；
- NULL 行；
- DELETE 删除主键行；
- 目标表自动建为 UNIQUE KEY 模型。

连接信息可通过系统属性覆盖：

```bash
-Ddm.url=jdbc:dm://127.0.0.1:5236 -Ddm.user=DATAY -Ddm.password=Datay123456 \
-Ddoris.host=127.0.0.1 -Ddoris.port=9030 -Ddoris.user=root -Ddoris.password= \
-Ddoris.database=datay_dm_cdc
```

任一环境不可用时用例自动跳过。

## 6. 注意事项

- Doris 的 `DELETE` 需目标表为 UNIQUE KEY 模型（merge-on-write），DataY 自动建表已默认满足。
- 达梦 `DBMS_LOGMNR` 只分析本地归档日志，`ARCH_DEST` 必须是数据库实例本机目录。
- 达梦 `RLOG_APPEND_LOGIC` 修改后需重启实例才生效。
- 生产环境请按组件文档为达梦采集账号授予最小权限，而非直接使用 DBA。
