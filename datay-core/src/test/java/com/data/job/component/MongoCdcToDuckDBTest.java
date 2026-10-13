package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MongoDB CDC（Change Streams）实时同步到 DuckDB 的端到端测试。
 *
 * <p>前置条件（本地 MongoDB <b>副本集</b>，单机模式不支持 Change Streams）：
 * <pre>
 * docker run -d --name datay-mongo-cdc -p 27017:27017 mongo:7 --replSet rs0 --bind_ip_all
 * docker exec datay-mongo-cdc mongosh --eval \
 *   'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
 * </pre>
 * 也可直接执行 {@code src/test/docker/mongodb-replica-set/start.sh}。
 * 实例不可用或非副本集时测试自动跳过。MongoDB 地址可用系统属性 {@code mongo.url} 覆盖。
 */
public class MongoCdcToDuckDBTest {

    private static final String MONGO_URL = System.getProperty("mongo.url", "mongodb://127.0.0.1:27017");
    private static final String MONGO_URI = MONGO_URL + "/?directConnection=true";
    private static final String MONGO_DB = "test";
    private static final String COLLECTION = "cdc_demo";

    private static final String DUCK_URL = "jdbc:duckdb:./target/mongo-cdc-duckdb-test.db";
    private static final String SNAPSHOT_DUCK_URL = "jdbc:duckdb:./target/mongo-cdc-snapshot-duckdb-test.db";

    @Test
    public void testMongoCdcToDuckDB() throws Exception {
        assumeTrue(isReplicaSetAvailable(), "本地 MongoDB 不可用或未启用副本集，跳过测试");
        resetEnvironment(DUCK_URL);

        runJob("mongoCdcToDuckDB.json", DUCK_URL, () -> {
            awaitTrue(this::changeStreamActive, 30000, "等待 MongoDB Change Streams 采集器就绪超时");

            insertDocument(new Document("_id", 1).append("name", "alice").append("amount", 10.5));
            insertDocument(new Document("_id", 2).append("name", "bob").append("amount", 20.0));
            awaitTrue(() -> queryDuckCount(DUCK_URL) == 2, 30000, "等待 insert 同步到 DuckDB 超时");
            assertEquals("alice", queryDuckString(DUCK_URL, "select name from main." + COLLECTION + " where _id = '1'"));
            assertEquals(
                "20.0",
                queryDuckString(DUCK_URL, "select cast(amount as varchar) from main." + COLLECTION + " where _id = '2'")
            );

            updateDocument(1, "alice2", 11.11);
            awaitTrue(
                () -> "alice2".equals(queryDuckString(DUCK_URL, "select name from main." + COLLECTION + " where _id = '1'")),
                30000,
                "等待 update 同步到 DuckDB 超时"
            );

            deleteDocument(2);
            awaitTrue(() -> queryDuckCount(DUCK_URL) == 1, 30000, "等待 delete 同步到 DuckDB 超时");
            assertEquals("alice2", queryDuckString(DUCK_URL, "select name from main." + COLLECTION + " where _id = '1'"));
        });
    }

    @Test
    public void testMongoCdcSnapshotToDuckDB() throws Exception {
        assumeTrue(isReplicaSetAvailable(), "本地 MongoDB 不可用或未启用副本集，跳过测试");
        resetEnvironment(SNAPSHOT_DUCK_URL);

        insertDocument(new Document("_id", 1).append("name", "snap-1"));
        insertDocument(new Document("_id", 2).append("name", "snap-2"));

        runJob("mongoCdcSnapshotToDuckDB.json", SNAPSHOT_DUCK_URL, () -> {
            awaitTrue(() -> queryDuckCount(SNAPSHOT_DUCK_URL) == 2, 30000, "等待全量快照同步到 DuckDB 超时");
            awaitTrue(this::changeStreamActive, 30000, "等待快照后的增量采集器就绪超时");

            insertDocument(new Document("_id", 3).append("name", "snap-3"));
            awaitTrue(() -> queryDuckCount(SNAPSHOT_DUCK_URL) == 3, 30000, "等待快照后的增量同步到 DuckDB 超时");
            assertEquals("snap-3", queryDuckString(SNAPSHOT_DUCK_URL, "select name from main." + COLLECTION + " where _id = '3'"));
        });
    }

    /**
     * 启动作业并在给定断言块执行期间运行，结束后取消任务并清理。
     */
    private void runJob(String jobFile, String duckUrl, ThrowingRunnable assertions) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(
            java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/" + jobFile)
            )
        ).replace("mongodb://127.0.0.1:27017", MONGO_URL);

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            assertions.run();
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            closeDuck(duckUrl);
            deleteDuckFile(duckUrl);
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private boolean isReplicaSetAvailable() {
        try (MongoClient client = openMongo()) {
            Document hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            return hello.getString("setName") != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean changeStreamActive() {
        try (MongoClient client = openMongo()) {
            Document currentOp = client
                .getDatabase("admin")
                .runCommand(new Document("currentOp", 1).append("$all", true));
            List<Document> inprog = currentOp.getList("inprog", Document.class);
            if (inprog == null) {
                return false;
            }
            for (Document entry : inprog) {
                // 变更流在空闲等待时表现为 getMore 操作，其 originatingCommand 中带有 $changeStream
                if (entry.toJson().contains("$changeStream")) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment(String duckUrl) throws Exception {
        closeDuck(duckUrl);
        deleteDuckFile(duckUrl);
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        try (MongoClient client = openMongo()) {
            client.getDatabase(MONGO_DB).getCollection(COLLECTION).drop();
        }
    }

    private void insertDocument(Document document) {
        try (MongoClient client = openMongo()) {
            client.getDatabase(MONGO_DB).getCollection(COLLECTION).insertOne(document);
        }
    }

    private void updateDocument(int id, String name, double amount) {
        try (MongoClient client = openMongo()) {
            client
                .getDatabase(MONGO_DB)
                .getCollection(COLLECTION)
                .updateOne(
                    new Document("_id", id),
                    new Document("$set", new Document("name", name).append("amount", amount))
                );
        }
    }

    private void deleteDocument(int id) {
        try (MongoClient client = openMongo()) {
            client.getDatabase(MONGO_DB).getCollection(COLLECTION).deleteOne(new Document("_id", id));
        }
    }

    private int queryDuckCount(String duckUrl) {
        Integer count = queryDuckInt(duckUrl, "select count(*) from main." + COLLECTION);
        return count == null ? -1 : count;
    }

    private Integer queryDuckInt(String duckUrl, String sql) {
        try (Connection conn = duckConnection(duckUrl); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryDuckString(String duckUrl, String sql) {
        try (Connection conn = duckConnection(duckUrl); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection duckConnection(String duckUrl) throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(duckUrl);
        return DBUtils.getConnection(duck);
    }

    private void closeDuck(String duckUrl) {
        try {
            DBUtils.closePool(duckUrl, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile(String duckUrl) {
        String prefix = duckUrl.substring("jdbc:duckdb:".length());
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File(prefix + suffix);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private MongoClient openMongo() {
        return MongoClients.create(MONGO_URI);
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(false, message);
    }

    private boolean isCancellation(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof InterruptedException) {
                return true;
            }
            String message = throwable.getMessage();
            if (message != null && message.contains("InterruptedException")) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
