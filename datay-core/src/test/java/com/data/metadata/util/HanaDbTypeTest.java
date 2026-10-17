package com.data.metadata.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.data.metadata.DBType;
import org.junit.jupiter.api.Test;

class HanaDbTypeTest {

    @Test
    void recognizesHanaJdbcUrl() {
        String url = "jdbc:sap://localhost:39015/?databaseName=HXE";
        assertEquals(DBType.HANA.toString(), DBUtils.getDBType(url));
        assertEquals(DBType.HANA, DBUtils.getDBTypeEnum(url));
    }
}
