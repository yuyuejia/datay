package com.data.datafusion.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;
import org.apache.commons.dbutils.BasicRowProcessor;

/**
 * 解决查询出相同字段问题
 * Created on 2019/8/23.
 */
public class MultiRowProcessor extends BasicRowProcessor {

    @Override
    public Map<String, Object> toMap(ResultSet rs) throws SQLException {
        Map<String, Object> result = new CaseInsensitiveHashMap();
        ResultSetMetaData rsmd = rs.getMetaData();
        int cols = rsmd.getColumnCount();

        for (int i = 1; i <= cols; ++i) {
            String columnName = rsmd.getColumnLabel(i);
            if (null == columnName || 0 == columnName.length()) {
                columnName = rsmd.getColumnName(i);
            }
            result = putValue(result, columnName, rs.getObject(i), 1);
        }
        return result;
    }

    private Map<String, Object> putValue(Map<String, Object> result, String columnName, Object v, int i) {
        String col = "";
        if (i == 1) {
            col = columnName;
        } else {
            col = columnName + "_" + i;
        }
        if (!result.containsKey(col)) {
            result.put(col, v);
            return result;
        } else {
            int j = i + 1;
            return putValue(result, columnName, v, j);
        }
    }

    private static class CaseInsensitiveHashMap extends LinkedHashMap<String, Object> {

        private final Map<String, String> lowerCaseMap;
        private static final long serialVersionUID = -2848100435296897392L;

        private CaseInsensitiveHashMap() {
            this.lowerCaseMap = new HashMap();
        }

        @Override
        public boolean containsKey(Object key) {
            Object realKey = this.lowerCaseMap.get(key.toString().toLowerCase(Locale.ENGLISH));
            return super.containsKey(realKey);
        }

        @Override
        public Object get(Object key) {
            Object realKey = this.lowerCaseMap.get(key.toString().toLowerCase(Locale.ENGLISH));
            return super.get(realKey);
        }

        @Override
        public Object put(String key, Object value) {
            Object oldKey = this.lowerCaseMap.put(key.toLowerCase(Locale.ENGLISH), key);
            Object oldValue = super.remove(oldKey);
            super.put(key, value);
            return oldValue;
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            Iterator i$ = m.entrySet().iterator();

            while (i$.hasNext()) {
                Map.Entry<? extends String, ?> entry = (Map.Entry) i$.next();
                String key = (String) entry.getKey();
                Object value = entry.getValue();
                this.put(key, value);
            }
        }

        @Override
        public Object remove(Object key) {
            Object realKey = this.lowerCaseMap.remove(key.toString().toLowerCase(Locale.ENGLISH));
            return super.remove(realKey);
        }
    }
}
