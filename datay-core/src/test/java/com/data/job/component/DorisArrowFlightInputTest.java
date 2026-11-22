package com.data.job.component;

import com.data.job.ComponentFactory;
import com.data.job.DAGParser;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DorisArrowFlightInput 查询构建与调试采样测试。
 */
class DorisArrowFlightInputTest extends ComponentTestBase {

    private DorisArrowFlightInput component;
    private TableMeta table;

    @BeforeEach
    void setUp() {
        component = new DorisArrowFlightInput();
        initComponent(component, "doris-arrow-input-test", "DorisArrowFlightInput");

        table = new TableMeta();
        table.setSchema("demo");
        table.setTable("orders");

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DORIS");
        datasource.setUrl("jdbc:mysql://doris-fe:9030/demo");
        datasource.setHostname("doris-fe");
        datasource.setDbschema("demo");
        component.setDatasource(datasource);
    }

    @Test
    void appendsDebugLimitByDefault() {
        context.setDebugMode(true);

        assertEquals(
            "SELECT * FROM `demo`.`orders` LIMIT 100",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void respectsConfiguredDebugRowLimit() {
        context.setDebugMode(true);
        context.setDebugRowLimit(10);

        assertEquals(
            "SELECT * FROM `demo`.`orders` LIMIT 10",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void doesNotAppendLimitWhenNotDebug() {
        assertFalse(component.buildQuery(table, null, null).contains("LIMIT"));
    }

    @Test
    void omitsNullSchema() {
        context.setDebugMode(true);
        table.setSchema(null);
        component.setSchema(null);
        component.getDatasource().setDbschema(null);

        assertEquals(
            "SELECT * FROM `orders` LIMIT 100",
            component.buildQuery(table, null, null)
        );
    }

    @Test
    void appendsWhereIncrementAndOrderByBeforeLimit() {
        context.setDebugMode(true);
        component.setWhere("status = 'PAID'");
        component.setLastIncrValue("orders", 100);

        assertEquals(
            "SELECT * FROM `demo`.`orders` WHERE (status = 'PAID') AND `id` > 100 ORDER BY `id` ASC LIMIT 100",
            component.buildQuery(table, "id", null)
        );
    }

    @Test
    void formatsTimestampIncrementLiteral() {
        context.setDebugMode(true);
        component.setLastIncrValue("orders", java.time.LocalDateTime.of(2026, 11, 21, 10, 30, 0));

        assertEquals(
            "SELECT * FROM `demo`.`orders` WHERE `update_time` > '2026-11-21 10:30:00.000' ORDER BY `update_time` ASC LIMIT 100",
            component.buildQuery(table, "update_time", null)
        );
    }

    @Test
    void resolvesWhereDynamicParameters() {
        FlowFile flowFile = createStartFlowFile();
        flowFile.setAttribute("stat_date", "2026-11-21");
        component.setWhere("stat_date = '${attr:stat_date}'");

        String query = component.buildQuery(table, null, flowFile);
        assertTrue(query.contains("stat_date = '2026-11-21'"));
    }

    @Test
    void comparesNumericAndTemporalIncrementValues() {
        assertTrue(component.compareIncrValue(10, 2) > 0);
        assertTrue(component.compareIncrValue(100, "99") > 0);
        assertTrue(component.compareIncrValue(
            java.time.LocalDateTime.of(2026, 11, 21, 10, 0),
            java.time.LocalDateTime.of(2026, 11, 20, 10, 0)
        ) > 0);
        assertTrue(component.compareIncrValue("b", "a") > 0);
    }

    @Test
    void exposesDynamicFormMetadata() {
        assertNotNull(ComponentFactory.getDescriptor("DorisArrowFlightInput"));
        assertTrue(
            ComponentFactory.getFields("DorisArrowFlightInput")
                .stream()
                .anyMatch(field -> "sourceId".equals(field.getKey()))
        );
        assertTrue(
            ComponentFactory.getFields("DorisArrowFlightInput")
                .stream()
                .anyMatch(field -> "arrowFlightPort".equals(field.getKey()))
        );
        // Schema 在数据源选择弹窗中确定，不再单独暴露表单字段
        assertTrue(
            ComponentFactory.getFields("DorisArrowFlightInput")
                .stream()
                .noneMatch(field -> "schema".equals(field.getKey()))
        );
    }

    @Test
    void createsComponentFromTaskConfig() {
        Map<String, Object> datasourceConfig = new HashMap<>();
        datasourceConfig.put("type", "DORIS");
        datasourceConfig.put("url", "jdbc:mysql://doris-fe:9030/demo");
        datasourceConfig.put("username", "root");
        datasourceConfig.put("password", "");

        Map<String, Object> params = new HashMap<>();
        params.put("sourceId", datasourceConfig);
        params.put("schema", "demo");
        params.put("table", "orders");
        params.put("arrowFlightPort", 8070);

        DorisArrowFlightInput created = (DorisArrowFlightInput) ComponentFactory.create(
            "DorisArrowFlightInput",
            params
        );
        assertNotNull(created.getDatasource());
        assertEquals("DORIS", created.getDatasource().getType());
        assertEquals("demo", created.getSchema());
        assertEquals("orders", created.getTable());
        assertEquals(8070, created.getArrowFlightPort());
    }

    @Test
    void dagParserCopiesSourceIdToDatasource() throws Exception {
        DAGParser parser = new DAGParser();
        parser.parse(
            "{\"units\":[{\".id\":\"n1\",\".name\":\"DorisArrowFlightInput\",\"sourceId\":{\"type\":\"DORIS\",\"url\":\"jdbc:mysql://doris-fe:9030/demo\"},\"schema\":\"demo\",\"table\":\"orders\"}]}"
        );
        DorisArrowFlightInput created = (DorisArrowFlightInput) parser.topologicalSort().get(0);
        assertNotNull(created.getSourceId());
        assertEquals("DORIS", created.getSourceId().getType());
    }
}
