package com.data.datafusion.service.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SqlLineageParserTest {

    private final SqlLineageParser parser = new SqlLineageParser();

    @Test
    void shouldExtractSelectSourceTables() {
        SqlReferences references = parser.parse("SELECT * FROM sales.orders o JOIN dim_store s ON o.store_id = s.id");

        assertThat(names(references.reads())).containsExactlyInAnyOrder("sales.orders", "dim_store");
        assertThat(references.writes()).isEmpty();
    }

    @Test
    void shouldIgnoreCteAlias() {
        SqlReferences references = parser.parse(
            "WITH recent AS (SELECT * FROM sales.orders) SELECT * FROM recent r JOIN dim_store s ON r.store_id = s.id"
        );

        assertThat(names(references.reads())).containsExactlyInAnyOrder("sales.orders", "dim_store");
    }

    @Test
    void shouldExtractInsertTargetAndSource() {
        SqlReferences references = parser.parse("INSERT INTO dw.fact_sales SELECT * FROM ods.orders");

        assertThat(names(references.writes())).containsExactly("dw.fact_sales");
        assertThat(names(references.reads())).containsExactly("ods.orders");
    }

    @Test
    void shouldExtractCreateTableAsSelect() {
        SqlReferences references = parser.parse("CREATE TABLE dw.fact_sales AS SELECT * FROM ods.orders");

        assertThat(names(references.writes())).containsExactly("dw.fact_sales");
        assertThat(names(references.reads())).containsExactly("ods.orders");
    }

    @Test
    void shouldExtractUpdateDeleteAndMergeTargets() {
        SqlReferences update = parser.parse("UPDATE dw.fact_sales SET amount = 1 WHERE id = 1");
        SqlReferences delete = parser.parse("DELETE FROM dw.fact_sales WHERE id = 1");
        SqlReferences merge = parser.parse(
            "MERGE INTO dw.fact_sales t USING ods.orders o ON t.id = o.id WHEN MATCHED THEN UPDATE SET amount = o.amount"
        );

        assertThat(names(update.writes())).containsExactly("dw.fact_sales");
        assertThat(names(delete.writes())).containsExactly("dw.fact_sales");
        assertThat(names(merge.writes())).containsExactly("dw.fact_sales");
        assertThat(names(merge.reads())).containsExactly("ods.orders");
    }

    @Test
    void shouldIgnoreCommentsAndStringLiterals() {
        SqlReferences references = parser.parse(
            "SELECT * FROM sales.orders -- FROM fake_table\nWHERE name = 'from another_fake' /* JOIN x */"
        );

        assertThat(names(references.reads())).containsExactly("sales.orders");
    }

    @Test
    void shouldFallbackForUnsupportedDialect() {
        SqlReferences references = parser.parse("SELECT * FROM sales.orders WHERE");

        assertThat(references.inferred()).isTrue();
        assertThat(names(references.reads())).contains("sales.orders");
    }

    @Test
    void shouldIgnoreCteAliasWhenFallingBack() {
        SqlReferences references = parser.parse(
            "WITH recent AS (SELECT * FROM sales.orders) SELECT * FROM recent WHERE"
        );

        assertThat(references.inferred()).isTrue();
        assertThat(names(references.reads())).containsExactly("sales.orders");
    }

    private Set<String> names(Set<TableRef> tables) {
        return tables.stream().map(TableRef::displayName).collect(Collectors.toSet());
    }
}
