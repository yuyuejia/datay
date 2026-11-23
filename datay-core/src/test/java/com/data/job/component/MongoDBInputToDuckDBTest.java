package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import com.data.status.LocalFileStatusStorageStrategy;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MongoDBInput 流式全量同步到 DuckDB 的端到端测试。
 *
 * <p>数据量远大于 batchSize（5000 条 / 每批 200 条），用于验证游标流式读取、分批下发
 * 的完整链路，避免回归为「一次性物化全量结果」。
 *
 * <p>前置条件（本地 MongoDB，单机即可）：
 * <pre>
 * docker run -d --name datay-mongo -p 27017:27017 mongo:7
 * </pre>
 * 地址可用系统属性 {@code mongo.url} 覆盖。实例不可用时测试自动跳过。
 */
public class MongoDBInputToDuckDBTest {

    private static final String MONGO_URL = System.getProperty("mongo.url", "mongodb://127.0.0.1:27017");
    // 实例不可用时尽快失败并跳过，避免默认 30s 的服务器选择超时
    private static final String MONGO_URI =
        MONGO_URL + (MONGO_URL.contains("?") ? "&" : "/?") + "directConnection=true&serverSelectionTimeoutMS=2000";
    private static final String MONGO_DB = "test";
    private static final String COLLECTION = "mongo_input_demo";
    private static final int ROWS = 5000;

    private static final String DUCK_URL = "jdbc:duckdb:./target/mongo-input-duckdb-test.db";

    // 使用独立的状态目录，避免读取历史/其他用例遗留的增量水位
    private static final String STATUS_DIR = "./target/mongo-input-status";

    @Test
    @Timeout(120000)
    public void testStreamingImportToDuckDB() throws Exception {
        assumeTrue(isMongoAvailable(), "本地 MongoDB 不可用，跳过测试");
        resetEnvironment();
        insertDocuments(0, ROWS);

        runJob();

        assertEquals(ROWS, queryDuckInt("select count(*) from main." + COLLECTION));
        assertEquals("0", queryDuckString("select cast(seq as varchar) from main." + COLLECTION + " where _id = 0"));
        assertEquals(
            "row-" + (ROWS - 1),
            queryDuckString("select name from main." + COLLECTION + " where _id = " + (ROWS - 1))
        );
    }

    /**
     * 验证增量水位随批次推进并持久化：第二次运行只读取水位之后新增的数据。
     */
    @Test
    @Timeout(120000)
    public void testIncrementalImportResumesFromLastBatch() throws Exception {
        assumeTrue(isMongoAvailable(), "本地 MongoDB 不可用，跳过测试");
        resetEnvironment();
        insertDocuments(0, 100);

        runJob();
        assertEquals(100, queryDuckInt("select count(*) from main." + COLLECTION));

        insertDocuments(100, 150);
        runJob();

        assertEquals(150, queryDuckInt("select count(*) from main." + COLLECTION));
        assertEquals(
            "row-149",
            queryDuckString("select name from main." + COLLECTION + " where _id = 149")
        );
    }

    private void runJob() throws Exception {
        String job = new String(
            Files.readAllBytes(Paths.get(System.getProperty("user.dir"), "src/test/mongoInputToDuckDB.json"))
        ).replace("mongodb://127.0.0.1:27017", MONGO_URL);
        ETLFlowTask runner = new ETLFlowTask();
        runner.setStatusStorageStrategy(new LocalFileStatusStorageStrategy(STATUS_DIR));
        runner.runJob(job);
    }

    private void insertDocuments(int fromInclusive, int toExclusive) {
        List<Document> documents = new ArrayList<>(Math.max(0, toExclusive - fromInclusive));
        for (int i = fromInclusive; i < toExclusive; i++) {
            documents.add(new Document("_id", i).append("seq", i).append("name", "row-" + i));
        }
        try (MongoClient client = openMongo()) {
            client.getDatabase(MONGO_DB).getCollection(COLLECTION).insertMany(documents);
        }
    }

    private boolean isMongoAvailable() {
        try (MongoClient client = openMongo()) {
            client.getDatabase("admin").runCommand(new Document("ping", 1));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private MongoClient openMongo() {
        return MongoClients.create(MONGO_URI);
    }

    private void resetEnvironment() {
        closeDuck();
        deleteDuckFile();
        deleteRecursively(new File(STATUS_DIR));
        try (MongoClient client = openMongo()) {
            client.getDatabase(MONGO_DB).getCollection(COLLECTION).drop();
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private Integer queryDuckInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryDuckString(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection duckConnection() throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(DUCK_URL);
        return DBUtils.getConnection(duck);
    }

    private void closeDuck() {
        try {
            DBUtils.closePool(DUCK_URL, null);
        } catch (Exception ignored) {
        }
    }

    private void deleteDuckFile() {
        String prefix = DUCK_URL.substring("jdbc:duckdb:".length());
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File(prefix + suffix);
            if (file.exists()) {
                assertTrue(file.delete(), "删除测试 DuckDB 文件失败: " + file.getAbsolutePath());
            }
        }
    }
}
