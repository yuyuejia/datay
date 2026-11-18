package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.data.metadata.TableMeta;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SqliteDataSourceProviderTest {

    @Test
    void tableListIncludesViews() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t_base(id INTEGER PRIMARY KEY)");
            stmt.execute("CREATE VIEW v_test AS SELECT id FROM t_base");

            List<TableMeta> tables = new SqliteDataSourceProvider().getTables(conn, "main", null, null);

            assertEquals(
                Set.of("t_base", "v_test"),
                tables.stream().map(TableMeta::getTable).collect(Collectors.toSet())
            );
        }
    }
}
