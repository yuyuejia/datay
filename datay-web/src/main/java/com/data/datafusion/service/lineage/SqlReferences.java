package com.data.datafusion.service.lineage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 一段 SQL 解析出的读取表、写入表以及解析置信度。
 */
public record SqlReferences(Set<TableRef> reads, Set<TableRef> writes, boolean inferred) {
    public SqlReferences {
        reads = reads == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(reads));
        writes = writes == null ? Collections.emptySet() : Collections.unmodifiableSet(new LinkedHashSet<>(writes));
    }

    public static SqlReferences empty() {
        return new SqlReferences(Collections.emptySet(), Collections.emptySet(), false);
    }
}
