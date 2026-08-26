package com.data.expression;

import com.data.expression.ParameterType;

public class ParameterInfo {

    private String name;
    private ParameterType type;

    public ParameterInfo(String name, ParameterType type) {
        this.name = name;
        this.type = type;
    }

    public String getFullMatch() {
        if(type == ParameterType.HASH_CURLY) {
            return "#{" + name + "}";
        }
        return type == ParameterType.DOLLAR_CURLY_ATTR ? "${attr:" + name + "}" : "${" + name + "}";
    }

    public String getName() {
        return name;
    }

    public ParameterType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "ParameterInfo{name='" + name + "', type=" + type + "}";
    }
}
