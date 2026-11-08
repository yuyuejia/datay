package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataQualityTaskService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobService;
import com.data.datafusion.service.dto.AppPackageInitResultDTO;
import com.data.datafusion.service.dto.DataQualityRuleBindingDTO;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.apppackage.model.AppPackageDataQualityTask;
import com.data.datafusion.service.apppackage.model.AppPackageDataSource;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 验证资产包导入时能创建数据质量检核任务，以及包含 DataQualityCheck 的 ETL 任务。
 */
@IntegrationTest
@WithMockUser
class AppPackageDataQualityImportIT {

    @Autowired
    private AppPackageImportService importService;

    @Autowired
    private DataQualityTaskService dataQualityTaskService;

    @Autowired
    private DataQualityTaskRepository dataQualityTaskRepository;

    @Autowired
    private ETLTaskService etlTaskService;

    @Autowired
    private ETLTaskRepository etlTaskRepository;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private JobService jobService;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    private String dataQualityTaskId;

    private String etlTaskId;

    private String dataSourceId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        if (dataQualityTaskId != null) {
            try {
                dataQualityTaskService.delete(dataQualityTaskId);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
        if (etlTaskId != null) {
            try {
                etlTaskService.delete(etlTaskId);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
        if (dataSourceId != null) {
            try {
                dataSourceService.delete(dataSourceId);
            } catch (Exception ignored) {
                // ignore cleanup failure
            }
        }
        TenantContext.clear();
    }

    @Test
    void shouldImportQualityTaskAndEtlTaskWithQualityCheckComponent() throws Exception {
        AppPackageContent content = buildContent();
        AppPackageInitResultDTO result = importService.importContent(content, null, null);

        assertThat(result.counts.get("dataQualityTasks")).isEqualTo(1);
        assertThat(result.counts.get("etlTasks")).isEqualTo(1);
        dataQualityTaskId = result.idMapping.get("dataQualityTasks").get("9300");
        etlTaskId = result.idMapping.get("etlTasks").get("6013");
        dataSourceId = result.idMapping.get("dataSources").get("9001");
        assertThat(dataQualityTaskId).isNotBlank();
        assertThat(etlTaskId).isNotBlank();
        assertThat(dataSourceId).isNotBlank();

        DataQualityTask qualityTask = dataQualityTaskRepository.findById(dataQualityTaskId).orElseThrow();
        assertThat(qualityTask.getTableName()).isEqualTo("fact_sales_order");
        assertThat(qualityTask.getDataSourceId()).isEqualTo(dataSourceId);
        assertThat(qualityTask.getRuleConfigs()).contains("NOT_NULL", "order_id");

        ETLTask etlTask = etlTaskRepository.findById(etlTaskId).orElseThrow();
        assertThat(etlTask.getJobId()).isNotBlank();
        String jobContext = jobService
            .findOneJob(etlTask.getJobId())
            .orElseThrow()
            .getJobContext();
        assertThat(jobContext).contains("DataQualityCheck", "\"checkMode\":\"STREAM\"", "\"outputFormat\":\"CSV\"");
        String inputNodeConfig = etlTaskService
            .findOne(etlTaskId)
            .orElseThrow()
            .getNodes()
            .stream()
            .filter(node -> "StreamJdbcInput".equals(node.getType()))
            .findFirst()
            .orElseThrow()
            .getConfig();
        assertThat(inputNodeConfig).contains(dataSourceId);
    }

    private AppPackageContent buildContent() throws Exception {
        AppPackageContent content = new AppPackageContent();
        content.getMeta().setName("数据质量导入验证");
        content.getMeta().setCode("dq_import_test_" + System.currentTimeMillis());

        AppPackageDataSource dataSource = new AppPackageDataSource();
        dataSource.oldId = "9001";
        dataSource.name = "数据质量导入测试源";
        dataSource.type = "DUCKDB";
        dataSource.url = "jdbc:duckdb:" + tempDir.resolve("source.duckdb").toAbsolutePath();
        dataSource.schemaName = "main";
        content.setDataSources(List.of(dataSource));

        DataQualityRuleBindingDTO notNull = new DataQualityRuleBindingDTO();
        notNull.setRuleCode("IT_DQ_NOT_NULL");
        notNull.setRuleName("订单号非空");
        notNull.setRuleLevel("FIELD");
        notNull.setRuleType("NOT_NULL");
        notNull.setColumn("order_id");
        notNull.setSeverity("ERROR");
        notNull.setMaxInvalidRows(0L);
        notNull.setMaxInvalidRatio(0.0D);
        notNull.setConfig(Map.of("trimWhitespace", true));

        AppPackageDataQualityTask qualityTask = new AppPackageDataQualityTask();
        qualityTask.oldId = "9300";
        qualityTask.taskName = "资产包质量检核任务-" + System.currentTimeMillis();
        qualityTask.taskDesc = "验证资产包导入质量任务";
        qualityTask.dataSourceId = "9001";
        qualityTask.schemaName = "main";
        qualityTask.tableName = "fact_sales_order";
        qualityTask.whereClause = "";
        qualityTask.failOnWarn = false;
        qualityTask.sampleLimit = 100;
        qualityTask.outputFormat = "CSV";
        qualityTask.outputDir = "data-quality";
        qualityTask.rules = List.of(notNull);
        content.setDataQualityTasks(List.of(qualityTask));

        Map<String, Object> inputConfig = new LinkedHashMap<>();
        inputConfig.put("sourceId", "9001");
        inputConfig.put("schema", "main");
        inputConfig.put("table", "fact_sales_order");
        inputConfig.put("where", "");
        inputConfig.put("incrColumn", "");

        Map<String, Object> checkRule = new LinkedHashMap<>();
        checkRule.put("ruleCode", "IT_ETL_DQ_NOT_NULL");
        checkRule.put("ruleName", "订单号非空");
        checkRule.put("ruleType", "NOT_NULL");
        checkRule.put("column", "order_id");
        checkRule.put("severity", "ERROR");
        checkRule.put("maxInvalidRows", 0);
        checkRule.put("maxInvalidRatio", 0);
        checkRule.put("config", Map.of("trimWhitespace", true));

        Map<String, Object> checkConfig = new LinkedHashMap<>();
        checkConfig.put("checkMode", "STREAM");
        checkConfig.put("failOnError", true);
        checkConfig.put("rules", List.of(checkRule));

        AppPackageEtlTask etlTask = new AppPackageEtlTask();
        etlTask.oldId = "6013";
        etlTask.jobOldId = "7015";
        etlTask.taskName = "资产包数据质量 ETL-" + System.currentTimeMillis();
        etlTask.taskCode = "it_dq_etl_" + System.currentTimeMillis();
        etlTask.taskDesc = "验证资产包导入包含 DataQualityCheck 的 ETL 任务";
        etlTask.dir = "/验证/数据质量";
        etlTask.type = TaskConstants.ETL_TASK_KIND_BATCH;
        etlTask.cron = "";
        etlTask.project = "quickstart";

        AppPackageEtlTask.AppPackageEtlNode input = new AppPackageEtlTask.AppPackageEtlNode();
        input.oldId = "5301";
        input.label = "读取销售事实";
        input.code = "n1";
        input.type = "StreamJdbcInput";
        input.config = objectMapper.valueToTree(inputConfig);
        input.xAxis = "0";
        input.yAxis = "0";

        AppPackageEtlTask.AppPackageEtlNode check = new AppPackageEtlTask.AppPackageEtlNode();
        check.oldId = "5302";
        check.label = "数据质量检核";
        check.code = "n2";
        check.type = "DataQualityCheck";
        check.config = objectMapper.valueToTree(checkConfig);
        check.xAxis = "300";
        check.yAxis = "0";
        etlTask.nodes = List.of(input, check);

        AppPackageEtlTask.AppPackageEtlEdge edge = new AppPackageEtlTask.AppPackageEtlEdge();
        edge.oldId = "5401";
        edge.code = "e1";
        edge.source = "n1";
        edge.target = "n2";
        etlTask.edges = List.of(edge);
        content.setEtlTasks(List.of(etlTask));
        return content;
    }
}
