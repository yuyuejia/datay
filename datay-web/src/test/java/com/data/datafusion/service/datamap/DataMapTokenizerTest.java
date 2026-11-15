package com.data.datafusion.service.datamap;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataMapTokenizerTest {

    private final DataMapTokenizer tokenizer = new DataMapTokenizer();

    @Test
    void shouldTokenizeChineseAndLatinNames() {
        DataMapNodeDTO node = new DataMapNodeDTO(
            "table:1:public.orders",
            DataMapAssetTypes.TABLE,
            "订单明细表",
            "order_detail",
            "订单明细数据",
            "public.order_detail",
            "1",
            "订单库",
            "public",
            "order_detail",
            0,
            null,
            null,
            0,
            Map.of()
        );

        List<DataMapTokenizer.DataMapToken> tokens = tokenizer.tokensFor(node);
        assertThat(tokens).extracting(DataMapTokenizer.DataMapToken::token).contains("订单", "明细", "order", "order_detail");
        assertThat(tokenizer.queryTokens("订单明细")).contains("订单", "明细");
        assertThat(tokenizer.queryTokens("order_detail")).contains("order_detail");
    }
}
