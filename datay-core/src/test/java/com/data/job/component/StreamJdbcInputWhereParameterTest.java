package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StreamJdbcInput where 条件动态参数测试。
 */
class StreamJdbcInputWhereParameterTest extends ComponentTestBase {

    private StreamJdbcInput component;
    private TableMeta table;

    @BeforeEach
    void setUp() {
        component = new StreamJdbcInput();
        initComponent(component, "stream-jdbc-input-test", "StreamJdbcInput");

        table = new TableMeta();
        table.setSchema("demo");
        table.setTable("orders");
    }

    @Test
    void replacesAttrParameter() {
        component.setWhere("status = '${attr:status}' AND stat_date >= '${attr:start_date}'");
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("status", "PAID");
        flowFile.setAttribute("start_date", "2026-10-01");

        assertEquals(
            "SELECT * FROM demo.orders WHERE status = 'PAID' AND stat_date >= '2026-10-01'",
            component.buildQuery(table, null, flowFile)
        );
    }

    @Test
    void replacesShortAttributeParameter() {
        component.setWhere("tenant_id = '${tenant_id}'");
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("tenant_id", "0000LT2ON7R546TLHP0000");

        assertEquals(
            "SELECT * FROM demo.orders WHERE tenant_id = '0000LT2ON7R546TLHP0000'",
            component.buildQuery(table, null, flowFile)
        );
    }

    @Test
    void replacesBuiltinParameter() {
        component.setWhere("updated_at >= '#{NOW - 1d, yyyy-MM-dd}'");
        FlowFile flowFile = new FlowFile();

        String query = component.buildQuery(table, null, flowFile);

        assertFalse(query.contains("#{"));
        assertTrue(query.matches(
            "SELECT \\* FROM demo\\.orders WHERE updated_at >= '\\d{4}-\\d{2}-\\d{2}'"
        ));
    }

    @Test
    void keepsLegacyYtenantIdPlaceholder() {
        component.setYtenantId("tenant-001");
        component.setWhere("(ytenant_id = '${ytenantid}')");
        FlowFile flowFile = new FlowFile();

        assertEquals(
            "SELECT * FROM demo.orders WHERE (ytenant_id = 'tenant-001')",
            component.buildQuery(table, null, flowFile)
        );
    }

    @Test
    void keepsUnknownParameter() {
        component.setWhere("id = ${missing}");
        FlowFile flowFile = new FlowFile();

        assertEquals(
            "SELECT * FROM demo.orders WHERE id = ${missing}",
            component.buildQuery(table, null, flowFile)
        );
    }

    @Test
    void appendsIncrementalConditionAfterResolvedWhere() {
        component.setWhere("status = '${attr:status}'");
        component.setLastIncrValue("orders", 100);
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("status", "PAID");

        assertEquals(
            "SELECT * FROM demo.orders WHERE status = 'PAID' AND id > 100",
            component.buildQuery(table, "id", flowFile)
        );
    }

    @Test
    void resolvesLegacyPlaceholderWithoutFlowFile() {
        component.setYtenantId("tenant-001");
        component.setWhere("(ytenant_id = '${ytenantid}') AND status = '${attr:status}'");

        assertEquals(
            "SELECT * FROM demo.orders WHERE (ytenant_id = 'tenant-001') AND status = '${attr:status}'",
            component.buildQuery(table, null, null)
        );
    }
}
