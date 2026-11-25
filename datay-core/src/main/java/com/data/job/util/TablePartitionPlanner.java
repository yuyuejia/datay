package com.data.job.util;

import com.data.metadata.ColumnMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 表分区规划器。
 * <p>
 * 根据分区列的最小值/最大值，把一张表按主键（或增量字段）切分为若干互不重叠、完整覆盖的区间，
 * 供 {@code GenerateTableSelectSql} 生成分区 SQL，也供 {@code StreamJdbcInput} 在并发读取时
 * 让每个线程只读取自己的区间。
 *
 * <p>支持三种策略，与历史实现保持一致：
 * <ul>
 *   <li>数值列：按数值范围均分；</li>
 *   <li>数字字符串列：按数值范围均分，并按原始长度补零后以字符串字面量输出；</li>
 *   <li>普通字符串列：按首字符（0-9、a-z）范围均分。</li>
 * </ul>
 *
 * <p>无法分区时（无最小值/最大值、类型不支持、区间为 0 等）返回空方案，由调用方回退为普通查询。
 */
public final class TablePartitionPlanner {

    private TablePartitionPlanner() {
    }

    /**
     * 查询分区列的最小值/最大值。
     *
     * @param conn           数据库连接
     * @param qualifiedTable 已带 schema 的表名
     * @param columnName     分区列
     * @return {@code [min, max]}；任一为空时返回 {@code null}
     */
    public static Object[] queryMinMax(Connection conn, String qualifiedTable, String columnName) throws SQLException {
        String sql = "SELECT MIN(" + columnName + "), MAX(" + columnName + ") FROM " + qualifiedTable;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                Object min = rs.getObject(1);
                Object max = rs.getObject(2);
                if (min == null || max == null || min.toString().isEmpty() || max.toString().isEmpty()) {
                    return null;
                }
                return new Object[]{min, max};
            }
        }
        return null;
    }

    /**
     * 判断列类型是否支持分区。
     */
    public static boolean supports(ColumnMeta column) {
        return column != null && (isNumericType(column.getType()) || isStringType(column.getType()));
    }

    /**
     * 生成分区方案。
     *
     * @param column         分区列
     * @param minValue       分区列最小值
     * @param maxValue       分区列最大值
     * @param partitionCount 期望分区数（实际分区数可能更少）
     * @return 分区方案；不可分区时返回空方案
     */
    public static Plan plan(ColumnMeta column, Object minValue, Object maxValue, int partitionCount) {
        if (column == null || minValue == null || maxValue == null || partitionCount <= 0) {
            return Plan.empty();
        }
        if (isNumericType(column.getType())) {
            return numericPlan(column, minValue.toString(), maxValue.toString(), partitionCount, false, 0);
        }
        if (isStringType(column.getType())) {
            String minStr = minValue.toString();
            String maxStr = maxValue.toString();
            if (isUnsignedInteger(minStr) && isUnsignedInteger(maxStr) && minStr.length() == maxStr.length()) {
                Plan numericPlan = numericPlan(column, minStr, maxStr, partitionCount, true, minStr.length());
                if (numericPlan.isPartitionable()) {
                    return numericPlan;
                }
            }
            return charPlan(column, minStr, maxStr, partitionCount);
        }
        return Plan.empty();
    }

    /**
     * 判断是否为数值类型。
     */
    public static boolean isNumericType(String type) {
        if (type == null) {
            return false;
        }
        String typeUpper = type.toUpperCase();
        return typeUpper.contains("INT") || typeUpper.contains("NUM")
            || typeUpper.contains("DECIMAL") || typeUpper.contains("FLOAT")
            || typeUpper.contains("DOUBLE") || typeUpper.contains("REAL");
    }

    /**
     * 判断是否为字符串类型。
     */
    public static boolean isStringType(String type) {
        if (type == null) {
            return false;
        }
        String typeUpper = type.toUpperCase();
        return typeUpper.contains("CHAR") || typeUpper.contains("TEXT")
            || typeUpper.contains("VARCHAR") || typeUpper.contains("STRING");
    }

    private static Plan numericPlan(ColumnMeta column, String minText, String maxText,
                                    int partitionCount, boolean quoted, int padWidth) {
        BigDecimal min = toBigDecimal(minText);
        BigDecimal max = toBigDecimal(maxText);
        if (min == null || max == null) {
            return Plan.empty();
        }
        BigDecimal range = max.subtract(min);
        if (range.signum() <= 0) {
            return Plan.empty();
        }

        // 实际分区数不超过区间大小，避免生成大量空区间
        int count = partitionCount;
        if (range.compareTo(BigDecimal.valueOf(partitionCount)) < 0) {
            count = Math.max(1, range.intValue());
        }
        BigDecimal partitionSize = range.divide(BigDecimal.valueOf(count), 0, RoundingMode.FLOOR);
        if (partitionSize.signum() <= 0) {
            partitionSize = BigDecimal.ONE;
        }

        List<Range> ranges = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BigDecimal start = min.add(partitionSize.multiply(BigDecimal.valueOf(i)));
            boolean last = i == count - 1;
            BigDecimal end = last ? max : start.add(partitionSize);
            ranges.add(new Range(formatLiteral(start, quoted, padWidth),
                formatLiteral(end, quoted, padWidth), last));
        }
        return new Plan(column, ranges);
    }

    private static Plan charPlan(ColumnMeta column, String minStr, String maxStr, int partitionCount) {
        if (minStr.isEmpty() || maxStr.isEmpty()) {
            return Plan.empty();
        }
        char minChar = minStr.charAt(0);
        char maxChar = maxStr.charAt(0);
        if (!isValidCharRange(minChar) || !isValidCharRange(maxChar)) {
            return Plan.empty();
        }

        int minVal = charToNumericValue(minChar);
        int maxVal = charToNumericValue(maxChar);
        int charRange = maxVal - minVal;
        if (charRange <= 0) {
            return Plan.empty();
        }

        int count = Math.min(partitionCount, charRange);
        int partitionSize = Math.max(1, charRange / count);

        List<Range> ranges = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int startVal = minVal + i * partitionSize;
            int endVal = startVal + partitionSize;
            // 首区间从首字符开始，避免 min 为多字符串时漏掉同首字符的较短字符串
            String start = String.valueOf(numericValueToChar(startVal));
            boolean last = i == count - 1;
            String end = last ? maxStr : String.valueOf(numericValueToChar(endVal));
            ranges.add(new Range(quote(start), quote(end), last));
        }
        return new Plan(column, ranges);
    }

    private static boolean isUnsignedInteger(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidCharRange(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z');
    }

    private static int charToNumericValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        return 10 + (c - 'a');
    }

    private static char numericValueToChar(int value) {
        if (value >= 0 && value <= 9) {
            return (char) ('0' + value);
        }
        if (value >= 10 && value <= 35) {
            return (char) ('a' + (value - 10));
        }
        return '0';
    }

    private static BigDecimal toBigDecimal(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatLiteral(BigDecimal value, boolean quoted, int padWidth) {
        String text;
        if (quoted && padWidth > 0) {
            text = String.format("%0" + padWidth + "d", value.toBigInteger());
        } else {
            text = value.stripTrailingZeros().toPlainString();
        }
        return quoted ? quote(text) : text;
    }

    private static String quote(String text) {
        return "'" + text + "'";
    }

    /**
     * 分区方案。
     */
    public static final class Plan {

        private static final Plan EMPTY = new Plan(null, Collections.emptyList());

        private final ColumnMeta column;
        private final List<Range> ranges;

        private Plan(ColumnMeta column, List<Range> ranges) {
            this.column = column;
            this.ranges = ranges;
        }

        public static Plan empty() {
            return EMPTY;
        }

        public boolean isPartitionable() {
            return column != null && !ranges.isEmpty();
        }

        public int size() {
            return ranges.size();
        }

        public String getColumnName() {
            return column == null ? null : column.getName();
        }

        /**
         * 获取指定分区的谓词；索引越界时返回 {@code null}。
         */
        public String predicate(int index) {
            if (!isPartitionable() || index < 0 || index >= ranges.size()) {
                return null;
            }
            return ranges.get(index).toPredicate(column.getName());
        }
    }

    /**
     * 单个分区区间，左闭右开（最后一个区间右闭）。
     */
    private static final class Range {

        private final String start;
        private final String end;
        private final boolean endInclusive;

        private Range(String start, String end, boolean endInclusive) {
            this.start = start;
            this.end = end;
            this.endInclusive = endInclusive;
        }

        private String toPredicate(String columnName) {
            return columnName + " >= " + start + " AND " + columnName
                + (endInclusive ? " <= " : " < ") + end;
        }
    }
}
