package com.data.datafusion.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlExtractorTest {

    @Test
    void extractsSingleFencedQuery() {
        String content = """
            下面是查询：

            ```sql
            SELECT id, name FROM users WHERE id = 1
            ```
            """;

        assertThat(SqlExtractor.extract(content, false)).isEqualTo("SELECT id, name FROM users WHERE id = 1;");
    }

    @Test
    void keepsAllStatementsInFencedBlockPrecededByComment() {
        String content = """
            生成的 SQL 如下：

            ```sql
            -- 第 1 步：更新已有记录，保证调度反复执行时不报主键冲突
            UPDATE test_db.vip_orders vo
            SET vo.total_amount = o.total_amount
            FROM test_db.orders o
            WHERE vo.id = o.id AND o.total_amount > 1000;

            -- 第 2 步：插入新记录
            INSERT INTO test_db.vip_orders (id, total_amount)
            SELECT o.id, o.total_amount
            FROM test_db.orders o
            WHERE o.total_amount > 1000;
            ```

            - 两条语句均已通过语法解析校验（未实际执行）。
            """;

        String sql = SqlExtractor.extract(content, true);

        assertThat(sql).contains("UPDATE test_db.vip_orders");
        assertThat(sql).contains("INSERT INTO test_db.vip_orders");
        assertThat(sql).doesNotContain("```");
        assertThat(sql).doesNotContain("两条语句均已通过语法解析校验");
    }

    @Test
    void joinsSeparateFencedBlocks() {
        String content = """
            ```sql
            UPDATE t SET a = 1 WHERE id = 1;
            ```

            ```sql
            INSERT INTO t (id, a) VALUES (2, 2);
            ```
            """;

        assertThat(SqlExtractor.extract(content, true))
            .isEqualTo("UPDATE t SET a = 1 WHERE id = 1;\n\nINSERT INTO t (id, a) VALUES (2, 2);")
            .doesNotContain("```");
    }

    @Test
    void extractsInlineCodeSqlAndStripsBackticks() {
        String content = "生成的 SQL 如下：`UPDATE t SET a = 1 WHERE id = 1;`";

        assertThat(SqlExtractor.extract(content, true)).isEqualTo("UPDATE t SET a = 1 WHERE id = 1;");
    }

    @Test
    void collectsMultipleBareStatementsWhenNoCodeFence() {
        String content = """
            UPDATE t SET a = 1 WHERE id = 1;

            INSERT INTO t (id, a) VALUES (2, 2);

            说明：以上两条语句按顺序执行。
            """;

        String sql = SqlExtractor.extract(content, true);

        assertThat(sql).contains("UPDATE t SET a = 1 WHERE id = 1");
        assertThat(sql).contains("INSERT INTO t (id, a) VALUES (2, 2)");
        assertThat(sql).doesNotContain("说明");
    }

    @Test
    void readOnlyModeRejectsWriteStatements() {
        String content = "```sql\nUPDATE t SET a = 1\n```";

        assertThat(SqlExtractor.extract(content, false)).isNull();
        assertThat(SqlExtractor.extract(content, true)).isEqualTo("UPDATE t SET a = 1;");
    }
}
