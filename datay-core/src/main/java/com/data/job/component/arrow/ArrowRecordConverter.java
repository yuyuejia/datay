package com.data.job.component.arrow;

import com.alibaba.fastjson2.JSONObject;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.Types;
import org.apache.arrow.vector.util.Text;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Arrow RecordBatch 行转换器。
 *
 * <p>把 Arrow 列式数据按行转换为现有 FlowFile 使用的 {@link JSONObject}，并统一处理
 * Arrow 特有的 {@link Text}、日期时间数值、复杂类型等，保证下游 JDBC / Doris 写入组件
 * 可以直接消费。</p>
 */
public final class ArrowRecordConverter {

    private ArrowRecordConverter() {}

    /**
     * 读取一行记录。
     *
     * @param root Arrow 批次
     * @param row  行下标（从 0 开始）
     * @return 以 Arrow 字段名为 key 的行记录
     */
    public static JSONObject toRecord(VectorSchemaRoot root, int row) {
        List<org.apache.arrow.vector.types.pojo.Field> fields = root.getSchema().getFields();
        List<FieldVector> vectors = root.getFieldVectors();
        JSONObject record = new JSONObject(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String name = fields.get(i).getName();
            record.put(name, readValue(vectors.get(i), row));
        }
        return record;
    }

    /**
     * 读取单个 Arrow 值。
     */
    public static Object readValue(FieldVector vector, int row) {
        if (vector == null || vector.isNull(row)) {
            return null;
        }

        Types.MinorType type = vector.getMinorType();
        Object raw = vector.getObject(row);
        switch (type) {
            case NULL:
                return null;
            case DATEDAY:
                return raw instanceof Number ? LocalDate.ofEpochDay(((Number) raw).longValue()) : normalize(raw);
            case DATEMILLI:
                return raw instanceof LocalDateTime ? ((LocalDateTime) raw).toLocalDate() : normalize(raw);
            case TIMESEC:
                return raw instanceof Number ? LocalTime.ofSecondOfDay(((Number) raw).longValue()) : normalize(raw);
            case TIMEMILLI:
                return raw instanceof LocalDateTime ? ((LocalDateTime) raw).toLocalTime() : normalize(raw);
            case TIMEMICRO:
                return raw instanceof Number
                    ? LocalTime.ofNanoOfDay(((Number) raw).longValue() * 1000L)
                    : normalize(raw);
            case TIMENANO:
                return raw instanceof Number ? LocalTime.ofNanoOfDay(((Number) raw).longValue()) : normalize(raw);
            case TIMESTAMPSECTZ:
                return raw instanceof Number ? epochSecondToLocalDateTime(((Number) raw).longValue()) : normalize(raw);
            case TIMESTAMPMILLITZ:
                return raw instanceof Number ? epochToLocalDateTime(((Number) raw).longValue(), 1L) : normalize(raw);
            case TIMESTAMPMICROTZ:
                return raw instanceof Number ? epochToLocalDateTime(((Number) raw).longValue(), 1_000L) : normalize(raw);
            case TIMESTAMPNANOTZ:
                return raw instanceof Number ? epochToLocalDateTime(((Number) raw).longValue(), 1_000_000L) : normalize(raw);
            case MAP:
                return toMap(raw);
            default:
                return normalize(raw);
        }
    }

    private static LocalDateTime epochSecondToLocalDateTime(long value) {
        return Instant.ofEpochSecond(value).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static LocalDateTime epochToLocalDateTime(long value, long unitsPerMilli) {
        long millis = value / unitsPerMilli;
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 把 Arrow / Java 驱动的原始值转换为可 JSON 序列化、可被下游 JDBC 写入的类型。
     */
    public static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Text) {
            return value.toString();
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        // 二进制保留 byte[]，与 JDBC 输入链路一致；调试展示时由 DebugResult 统一转 Base64。
        if (value instanceof byte[]) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(normalize(entry.getKey())), normalize(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(normalize(item));
            }
            return result;
        }
        // Duration / Period / PeriodDuration 等区间类型统一转字符串，避免不同驱动序列化差异。
        if (value instanceof TemporalAmount) {
            return value.toString();
        }
        return value;
    }

    private static Object toMap(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return normalize(map);
        }
        if (raw instanceof List<?> entries) {
            Map<String, Object> result = new LinkedHashMap<>(entries.size());
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> mapEntry)) {
                    continue;
                }
                Object key = normalize(mapEntry.get("key"));
                Object value = normalize(mapEntry.get("value"));
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            }
            return result;
        }
        return normalize(raw);
    }
}
