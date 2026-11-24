package com.data.job.component;

import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doris Stream Load 响应校验回归测试。
 *
 * <p>Doris 在 HTTP 200 时仍可能返回 {@code Status=Fail}，旧实现只看 HTTP 状态码，
 * 会把失败批次当成写入成功，表现为“任务成功但数据少了”。这里锁定校验行为。</p>
 */
public class DorisStreamLoadResponseTest {

    private DorisStreamLoad component;
    private TableMeta targetTable;

    @BeforeEach
    public void setUp() {
        component = new DorisStreamLoad();
        component.setId("doris_sink");
        component.setName("Doris写入");
        component.setTaskLogger(new TaskLogger("doris-stream-load-test", "doris-stream-load-test"));

        targetTable = new TableMeta();
        targetTable.setSchema("dwd");
        targetTable.setTable("dwd_visitor_order");
        targetTable.addColumn(new ColumnMeta("sellid", "VARCHAR"));
    }

    @Test
    public void successResponsePasses() {
        String body =
            "{\"TxnId\":1,\"Label\":\"l1\",\"Status\":\"Success\",\"Message\":\"OK\"," +
            "\"NumberTotalRows\":10000,\"NumberLoadedRows\":10000,\"NumberFilteredRows\":0}";
        assertDoesNotThrow(() -> component.validateStreamLoadResult(body, 10000, targetTable));
    }

    @Test
    public void failStatusThrowsEvenWithHttp200() {
        String body =
            "{\"TxnId\":1,\"Label\":\"l1\",\"Status\":\"Fail\"," +
            "\"Message\":\"too many filtered rows\",\"NumberTotalRows\":10000," +
            "\"NumberLoadedRows\":0,\"NumberFilteredRows\":10000,\"ErrorURL\":\"http://be/err\"}";
        IOException error = assertThrows(
            IOException.class,
            () -> component.validateStreamLoadResult(body, 10000, targetTable)
        );
        assertTrue(error.getMessage().contains("Status=Fail"));
        assertTrue(error.getMessage().contains("too many filtered rows"));
        assertTrue(error.getMessage().contains("dwd.dwd_visitor_order"));
    }

    @Test
    public void missingStatusThrows() {
        IOException error = assertThrows(
            IOException.class,
            () -> component.validateStreamLoadResult("{\"Message\":\"unexpected\"}", 100, targetTable)
        );
        assertTrue(error.getMessage().contains("Status"));
    }

    @Test
    public void invalidJsonThrows() {
        assertThrows(IOException.class, () -> component.validateStreamLoadResult("<html>error</html>", 100, targetTable));
    }

    @Test
    public void successWithFilteredRowsStillPassesButWarns() {
        String body =
            "{\"Status\":\"Success\",\"Message\":\"OK\",\"NumberTotalRows\":100," +
            "\"NumberLoadedRows\":97,\"NumberFilteredRows\":3}";
        assertDoesNotThrow(() -> component.validateStreamLoadResult(body, 100, targetTable));
    }
}
