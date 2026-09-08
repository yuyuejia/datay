package com.data.datafusion.config;

import com.alibaba.fastjson2.JSON;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter(autoApply = false)
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<String, String> map = JSON.parseObject(dbData, Map.class);
            return map != null ? map : new HashMap<>();
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}