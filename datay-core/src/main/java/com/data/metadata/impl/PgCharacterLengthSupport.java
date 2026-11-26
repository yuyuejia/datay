package com.data.metadata.impl;

import com.data.metadata.DatabaseConverter;
import java.util.Locale;
import java.util.Set;

/**
 * PostgreSQL 系源库字符类型长度换算支持。
 *
 * <p>PostgreSQL 及兼容库的 {@code varchar(n)/char(n)} 中 n 是字符数；而 Oracle、达梦、
 * Doris 等目标的 {@code VARCHAR(n)/CHAR(n)} 默认按字节计量。将字符数转换为 UTF-8 字节数时，
 * 中文等常见多字节字符最多占 3 字节，因此按 3 倍扩大是安全下限。</p>
 */
final class PgCharacterLengthSupport {

    /** UTF-8 中中文等常见多字节字符占 3 字节。 */
    static final int UTF8_BYTES_PER_CHAR = 3;

    /**
     * 字符类型长度按“字符数”而非“字节数”计量的源库。
     *
     * <p>Oracle、达梦等按字节声明长度的数据库不能套用该规则。</p>
     */
    private static final Set<String> CHARACTER_LENGTH_SOURCE_TYPES = Set.of(
        "postgresql", "kingbase", "gaussdb", "greenplum", "ymatrix"
    );

    private PgCharacterLengthSupport() {
    }

    /**
     * 是否为按字符数声明字符类型长度的源数据库。
     */
    static boolean isCharacterLengthSource(String sourceDbType) {
        return sourceDbType != null
            && CHARACTER_LENGTH_SOURCE_TYPES.contains(sourceDbType.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 源类型是否为字符类型（需要按 UTF-8 字节长度换算）。
     *
     * <p>只处理字符类型，避免把 UUID/RAW 等映射为目标 VARCHAR 的类型也乘 3。</p>
     */
    static boolean isCharacterSourceType(String sourceType) {
        if (sourceType == null) {
            return false;
        }
        String baseType = DatabaseConverter.stripTypeParameters(sourceType.toUpperCase(Locale.ROOT));
        switch (baseType) {
            case "VARCHAR":
            case "VARCHAR2":
            case "NVARCHAR":
            case "NVARCHAR2":
            case "CHAR":
            case "NCHAR":
            case "BPCHAR":
            case "CHARACTER":
            case "CHARACTER VARYING":
                return true;
            default:
                return false;
        }
    }

    /**
     * 是否没有明确的长度上限。
     *
     * <p>PostgreSQL 的 {@code varchar} 不指定长度时，JDBC {@code COLUMN_SIZE} 返回
     * {@link Integer#MAX_VALUE}；部分驱动用 0 表示未知长度。</p>
     */
    static boolean isUnbounded(int length) {
        return length <= 0 || length == Integer.MAX_VALUE;
    }

    /**
     * 字符数换算为 UTF-8 字节数（按每个字符最多 3 字节估算）。
     */
    static long utf8Bytes(int charLength) {
        return (long) charLength * UTF8_BYTES_PER_CHAR;
    }
}
