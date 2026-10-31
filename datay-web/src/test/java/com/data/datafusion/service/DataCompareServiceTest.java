package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataCompareServiceTest {

    @Test
    void shouldBuildEtlJobJsonWithCompareAndExportNodes() {
        DataCompareService service = new DataCompareService(null, null, null, null, null, null, null, null, null);

        DataCompareTask task = new DataCompareTask();
        task.setLeftTable("left_table");
        task.setRightTable("right_table");
        task.setLeftWhere("id > 0");
        task.setRightWhere("");

        DataSourceDTO left = new DataSourceDTO();
        left.setType("MySQL");
        left.setUrl("jdbc:mysql://localhost:3306/db1");
        left.setUsername("u");
        left.setPassword("p");

        DataSourceDTO right = new DataSourceDTO();
        right.setType("PostgreSQL");
        right.setUrl("jdbc:postgresql://localhost:5432/db2");
        right.setUsername("u2");
        right.setPassword("p2");

        String json = service.buildJobJson(task, left, right, "db1", "public", "COMPARE_SQL", "EXPORT_SQL");
        JSONObject job = JSONUtil.parseObj(json);

        assertThat(job.getJSONArray("units")).hasSize(6);
        assertThat(job.getJSONArray("connections")).hasSize(5);
        assertThat(job.toString()).contains("left_input").contains("right_input").contains("compare_sql").contains("export_sql");
        assertThat(job.toString()).contains("COMPARE_SQL").contains("EXPORT_SQL");
        assertThat(job.toString()).contains("left_table").contains("right_table");
        assertThat(job.toString()).contains("jdbc:mysql://localhost:3306/db1");
        assertThat(job.toString()).contains("jdbc:postgresql://localhost:5432/db2");
    }

    @Test
    void shouldBuildMultiTableEtlJobJson() {
        DataCompareService service = new DataCompareService(null, null, null, null, null, null, null, null, null);
        DataSourceDTO left = new DataSourceDTO();
        left.setType("MYSQL");
        left.setUrl("jdbc:mysql://localhost:3306/db1");
        DataSourceDTO right = new DataSourceDTO();
        right.setType("POSTGRESQL");
        right.setUrl("jdbc:postgresql://localhost:5432/db2");

        List<DataCompareService.TablePlan> plans = List.of(
            new DataCompareService.TablePlan(0, "orders", "orders", "", "", "COMPARE_0", "EXPORT_0", "r0.csv", "s0.csv", "CSV"),
            new DataCompareService.TablePlan(1, "users", "users", "", "", "COMPARE_1", "EXPORT_1", "r1.csv", "s1.csv", "CSV")
        );

        JSONObject job = JSONUtil.parseObj(service.buildJobJson(left, right, "db1", "public", plans, true));

        assertThat(job.getJSONArray("units")).hasSize(12);
        assertThat(job.getJSONArray("connections")).hasSize(10);
        assertThat(job.toString()).contains("left_input_0", "right_input_0", "compare_sql_0", "export_sql_0");
        assertThat(job.toString()).contains("left_input_1", "right_input_1", "compare_sql_1", "export_sql_1");
        assertThat(job.toString()).contains("COMPARE_0", "COMPARE_1", "EXPORT_0", "EXPORT_1");
    }

    @Test
    void shouldUseCountQueriesWhenCompareDetailDisabled() {
        DataCompareService service = new DataCompareService(null, null, null, null, null, null, null, null, null);
        DataSourceDTO left = new DataSourceDTO();
        left.setType("MYSQL");
        left.setUrl("jdbc:mysql://localhost:3306/db1");
        left.setUsername("u");
        DataSourceDTO right = new DataSourceDTO();
        right.setType("POSTGRESQL");
        right.setUrl("jdbc:postgresql://localhost:5432/db2");
        right.setUsername("u2");

        List<DataCompareService.TablePlan> plans = List.of(
            new DataCompareService.TablePlan(
                0,
                "orders",
                "orders",
                "id > 0",
                "",
                "COUNT_SUMMARY",
                "COUNT_EXPORT",
                null,
                "s0.csv",
                "CSV"
            )
        );

        JSONObject job = JSONUtil.parseObj(service.buildJobJson(left, right, "db1", "public", plans, false));
        String text = job.toString();

        assertThat(job.getJSONArray("units")).hasSize(6);
        assertThat(text).contains("SqlInput");
        assertThat(text).doesNotContain("StreamJdbcInput");
        assertThat(text).contains("SELECT COUNT(*) AS row_count FROM db1.orders WHERE id > 0");
        assertThat(text).contains("SELECT COUNT(*) AS row_count FROM public.orders");
    }
}
