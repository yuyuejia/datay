package com.data.job.component.cdc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmSqlRedoParserTest {

    @Test
    void shouldParseUpdateSetAndWhere() {
        String sql =
            "UPDATE \"DATAY\".\"CDC_DEMO\" SET \"NAME\" = 'alice2', \"AMOUNT\" = 11.11 " +
            "WHERE \"ID\" = 1 AND \"NAME\" = 'alice' AND \"AMOUNT\" = 10.5 " +
            "AND \"UPDATED_AT\" = TIMESTAMP'2024-01-01 10:00:00' AND ROWID = 'AAAAAAAAAAAAAAAAAB';";

        Map<String, String> setValues = DmSqlRedoParser.parseSetValues(sql);
        assertEquals("alice2", setValues.get("NAME"));
        assertEquals("11.11", setValues.get("AMOUNT"));

        Map<String, String> whereValues = DmSqlRedoParser.parseWhereValues(sql);
        assertEquals("1", whereValues.get("ID"));
        assertEquals("alice", whereValues.get("NAME"));
        assertEquals("2024-01-01 10:00:00", whereValues.get("UPDATED_AT"));
        assertFalse(whereValues.containsKey("ROWID"));
    }

    @Test
    void shouldParseDeleteWhere() {
        String sql =
            "DELETE FROM \"DATAY\".\"CDC_DEMO\" WHERE \"ID\" = 3 AND \"NAME\" = 'O''Brien' " +
            "AND \"AMOUNT\" = NULL AND ROWID = 'AAAAAAAAAAAAAAAAAD';";

        Map<String, String> whereValues = DmSqlRedoParser.parseWhereValues(sql);
        assertEquals("3", whereValues.get("ID"));
        assertEquals("O'Brien", whereValues.get("NAME"));
        assertTrue(whereValues.containsKey("AMOUNT"));
        assertNull(whereValues.get("AMOUNT"));
        assertFalse(whereValues.containsKey("ROWID"));
    }

    @Test
    void shouldParseInsertValues() {
        String sql =
            "INSERT INTO \"DATAY\".\"CDC_DEMO\"(\"ID\", \"NAME\", \"AMOUNT\", \"UPDATED_AT\") " +
            "VALUES(1, 'alice', 10.5, TIMESTAMP'2024-01-01 10:00:00');";

        Map<String, String> values = DmSqlRedoParser.parseInsertValues(sql);
        assertEquals("1", values.get("ID"));
        assertEquals("alice", values.get("NAME"));
        assertEquals("10.5", values.get("AMOUNT"));
        assertEquals("2024-01-01 10:00:00", values.get("UPDATED_AT"));
    }

    @Test
    void shouldIgnoreKeywordsInsideStringLiterals() {
        String sql =
            "UPDATE \"DATAY\".\"CDC_DEMO\" SET \"NAME\" = 'where and set' " +
            "WHERE \"ID\" = 1 AND \"NAME\" = 'where and set';";

        assertEquals("where and set", DmSqlRedoParser.parseSetValues(sql).get("NAME"));
        assertEquals("where and set", DmSqlRedoParser.parseWhereValues(sql).get("NAME"));
    }
}
