package com.data.datafusion.service.time;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 时间维度支持的时间粒度。
 *
 * <p>顺序固定为 年 &lt; 季 &lt; 月 &lt; 周 &lt; 日，时间维度按所选粒度生成
 * {@code {g}_id}/{@code {g}_name} 层级字段。各级取值示例：
 * <ul>
 *     <li>YEAR：2024 / 2024年</li>
 *     <li>QUARTER：2024Q1 / 2024年Q1</li>
 *     <li>MONTH：202401 / 2024-01</li>
 *     <li>WEEK：2024W03 / 2024年第3周（ISO 周）</li>
 *     <li>DAY：20240115 / 2024-01-15</li>
 * </ul>
 */
public enum TimeGranularity {
    YEAR(1, "年"),
    QUARTER(2, "季"),
    MONTH(3, "月"),
    WEEK(4, "周"),
    DAY(5, "日");

    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private final int order;
    private final String label;

    TimeGranularity(int order, String label) {
        this.order = order;
        this.label = label;
    }

    public int getOrder() {
        return order;
    }

    public String getLabel() {
        return label;
    }

    /** 层级字段 id 名，如 year_id。 */
    public String idFieldName() {
        return name().toLowerCase(Locale.ROOT) + "_id";
    }

    /** 层级字段 name 名，如 year_name。 */
    public String nameFieldName() {
        return name().toLowerCase(Locale.ROOT) + "_name";
    }

    /** 该粒度在给定日期上的 id 取值。 */
    public String idOf(LocalDate date) {
        return switch (this) {
            case YEAR -> String.valueOf(date.getYear());
            case QUARTER -> date.getYear() + "Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case MONTH -> String.format("%04d%02d", date.getYear(), date.getMonthValue());
            case WEEK -> {
                int weekYear = date.get(ISO_WEEK.weekBasedYear());
                int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
                yield weekYear + "W" + String.format("%02d", week);
            }
            case DAY -> String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        };
    }

    /** 该粒度在给定日期上的名称取值。 */
    public String nameOf(LocalDate date) {
        return switch (this) {
            case YEAR -> date.getYear() + "年";
            case QUARTER -> date.getYear() + "年Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case MONTH -> String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            case WEEK -> {
                int weekYear = date.get(ISO_WEEK.weekBasedYear());
                int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
                yield weekYear + "年第" + week + "周";
            }
            case DAY -> date.toString();
        };
    }

    /**
     * 解析逗号分隔的粒度配置，校验顺序与去重。
     *
     * @param csv 如 {@code YEAR,QUARTER,MONTH,DAY}
     * @return 按由粗到细排序的粒度列表
     * @throws IllegalArgumentException 配置为空、含非法值或顺序错误时抛出
     */
    public static List<TimeGranularity> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("请至少选择一个时间粒度");
        }
        Set<TimeGranularity> seen = new LinkedHashSet<>();
        int previous = 0;
        for (String token : csv.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            TimeGranularity granularity;
            try {
                granularity = valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("不支持的时间粒度：" + value);
            }
            if (!seen.add(granularity)) {
                throw new IllegalArgumentException("时间粒度重复：" + value);
            }
            if (granularity.getOrder() <= previous) {
                throw new IllegalArgumentException("时间粒度顺序必须为由粗到细：年、季、月、周、日");
            }
            previous = granularity.getOrder();
        }
        if (seen.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个时间粒度");
        }
        return new ArrayList<>(seen);
    }

    public static String toCsv(List<TimeGranularity> granularities) {
        StringBuilder sb = new StringBuilder();
        for (TimeGranularity granularity : granularities) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(granularity.name());
        }
        return sb.toString();
    }
}
