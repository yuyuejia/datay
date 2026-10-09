package com.data.datafusion.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 校验自研 {@link UuidV7} 实现符合 RFC 9562：格式、版本、变体、唯一性与单调有序性。
 */
class UuidV7Test {

    private static final String UUID_V7_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

    @Test
    void shouldGenerateWellFormedUuidV7() {
        for (int i = 0; i < 1000; i++) {
            String value = UuidV7.generate();
            assertThat(value).hasSize(36).matches(UUID_V7_PATTERN);

            UUID uuid = UUID.fromString(value);
            assertThat(uuid.version()).as("版本号应为 7").isEqualTo(7);
            assertThat(uuid.variant()).as("变体应为 RFC 4122").isEqualTo(2);
        }
    }

    @Test
    void shouldGenerateStrictlyIncreasingValues() {
        String previous = UuidV7.generate();
        for (int i = 0; i < 100_000; i++) {
            String current = UuidV7.generate();
            assertThat(current).as("同一毫秒内也必须严格递增").isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    void shouldGenerateUniqueValues() {
        int count = 200_000;
        Set<String> values = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            values.add(UuidV7.generate());
        }
        assertThat(values).hasSize(count);
    }

    @Test
    void shouldEncodeCurrentEpochMillis() {
        long before = System.currentTimeMillis();
        long timestamp = UUID.fromString(UuidV7.generate()).getMostSignificantBits() >>> 16;
        long after = System.currentTimeMillis();

        // 单调实现可能因计数器用尽将内部时间戳小幅前移，这里给一个宽松窗口。
        assertThat(timestamp).isBetween(before - 1000, after + 60_000);
    }
}
