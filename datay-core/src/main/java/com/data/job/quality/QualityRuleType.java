package com.data.job.quality;

/**
 * 数据质量规则类型。
 *
 * <p>规则类型决定检核 SQL 的生成方式。表级规则关注整表统计特征，字段级规则关注单个字段的
 * 空值、唯一性、取值范围、格式等特征。</p>
 */
public enum QualityRuleType {

    ROW_COUNT_MIN("ROW_COUNT_MIN", "表行数下限", QualityRuleLevel.TABLE, "校验表的总行数不小于指定值。"),
    ROW_COUNT_RANGE("ROW_COUNT_RANGE", "表行数范围", QualityRuleLevel.TABLE, "校验表的总行数处于指定区间。"),
    COMPOSITE_UNIQUE("COMPOSITE_UNIQUE", "组合字段唯一", QualityRuleLevel.TABLE, "校验多个字段的组合值在表中唯一。"),
    CUSTOM_SQL("CUSTOM_SQL", "自定义 SQL", QualityRuleLevel.TABLE, "通过自定义 SQL 返回检核的无效行数。"),
    NOT_NULL("NOT_NULL", "字段非空", QualityRuleLevel.FIELD, "校验字段不为 NULL，可选择将空白字符串视为空值。"),
    UNIQUE("UNIQUE", "字段唯一", QualityRuleLevel.FIELD, "校验字段值在表中唯一。"),
    RANGE("RANGE", "字段值范围", QualityRuleLevel.FIELD, "校验数值或日期字段处于指定区间。"),
    ENUM("ENUM", "字段枚举值", QualityRuleLevel.FIELD, "校验字段值属于允许的枚举集合。"),
    REGEX("REGEX", "字段正则格式", QualityRuleLevel.FIELD, "校验字段值匹配指定的正则表达式。"),
    LENGTH("LENGTH", "字段长度范围", QualityRuleLevel.FIELD, "校验字符串字段长度处于指定区间。"),
    FRESHNESS("FRESHNESS", "数据时效", QualityRuleLevel.FIELD, "校验时间字段不早于指定的小时数。");

    private final String code;

    private final String label;

    private final QualityRuleLevel level;

    private final String description;

    QualityRuleType(String code, String label, QualityRuleLevel level, String description) {
        this.code = code;
        this.label = label;
        this.level = level;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public QualityRuleLevel getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public static QualityRuleType of(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("规则类型不能为空");
        }
        String normalized = code.trim().toUpperCase();
        for (QualityRuleType type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的数据质量规则类型: " + code);
    }

    public enum QualityRuleLevel {
        TABLE,
        FIELD,
    }
}
