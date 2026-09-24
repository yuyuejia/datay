package com.data.datafusion.service.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link TimeGranularity} 的单元测试。
 */
class TimeGranularityTest {

    @Test
    void shouldFormatIdAndName() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        assertThat(TimeGranularity.YEAR.idOf(date)).isEqualTo("2024");
        assertThat(TimeGranularity.YEAR.nameOf(date)).isEqualTo("2024年");
        assertThat(TimeGranularity.QUARTER.idOf(date)).isEqualTo("2024Q1");
        assertThat(TimeGranularity.QUARTER.nameOf(date)).isEqualTo("2024年Q1");
        assertThat(TimeGranularity.MONTH.idOf(date)).isEqualTo("202401");
        assertThat(TimeGranularity.MONTH.nameOf(date)).isEqualTo("2024-01");
        assertThat(TimeGranularity.WEEK.idOf(date)).isEqualTo("2024W03");
        assertThat(TimeGranularity.WEEK.nameOf(date)).isEqualTo("2024年第3周");
        assertThat(TimeGranularity.DAY.idOf(date)).isEqualTo("20240115");
        assertThat(TimeGranularity.DAY.nameOf(date)).isEqualTo("2024-01-15");
    }

    @Test
    void weekShouldUseIsoWeekBasedYear() {
        LocalDate date = LocalDate.of(2024, 12, 30);
        assertThat(TimeGranularity.WEEK.idOf(date)).isEqualTo("2025W01");
    }

    @Test
    void shouldParseValidCsv() {
        List<TimeGranularity> levels = TimeGranularity.parse("YEAR,QUARTER,MONTH,DAY");
        assertThat(levels).containsExactly(
            TimeGranularity.YEAR,
            TimeGranularity.QUARTER,
            TimeGranularity.MONTH,
            TimeGranularity.DAY
        );
    }

    @Test
    void shouldRejectInvalidOrderAndDuplicates() {
        assertThatThrownBy(() -> TimeGranularity.parse("MONTH,YEAR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeGranularity.parse("YEAR,YEAR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeGranularity.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeGranularity.parse("FOO")).isInstanceOf(IllegalArgumentException.class);
    }
}
