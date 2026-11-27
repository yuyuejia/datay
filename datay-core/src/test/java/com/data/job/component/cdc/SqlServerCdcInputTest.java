package com.data.job.component.cdc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.data.job.ComponentFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SQL Server CDC 起始 LSN 配置归一化测试。
 *
 * <p>前端表单未填写起始 LSN 时会提交空字符串，必须统一转换为 null，
 * 否则首次运行会被误判为存在断点，导致快照不执行。
 */
class SqlServerCdcInputTest {

    @Test
    void shouldNormalizeBlankStartLsnToNull() {
        SQLServerCDCInput input = new SQLServerCDCInput();

        input.setStartLsn("");
        assertNull(input.getStartLsn());

        input.setStartLsn("   ");
        assertNull(input.getStartLsn());

        input.setStartLsn(null);
        assertNull(input.getStartLsn());
    }

    @Test
    void shouldTrimConfiguredStartLsn() {
        SQLServerCDCInput input = new SQLServerCDCInput();
        input.setStartLsn(" 0x0000002A0000003C0004 ");

        assertEquals("0x0000002A0000003C0004", input.getStartLsn());
    }

    @Test
    void shouldNormalizeBlankStartLsnFromComponentConfig() {
        Map<String, Object> params = new HashMap<>();
        params.put("startLsn", "");

        SQLServerCDCInput input = (SQLServerCDCInput) ComponentFactory.create("SQLServerCDCInput", params);

        assertNull(input.getStartLsn());
    }
}
