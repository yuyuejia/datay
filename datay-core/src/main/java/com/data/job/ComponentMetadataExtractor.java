package com.data.job;

import cn.hutool.core.util.StrUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件配置字段元数据提取器。
 * <p>
 * 扫描组件类及其父类上所有带 {@link ComponentField} 注解的字段，生成前端动态表单所需的
 * {@link ComponentFieldDescriptor} 列表。子类同 key 的字段会覆盖父类声明。
 */
public final class ComponentMetadataExtractor {

    private ComponentMetadataExtractor() {}

    /**
     * 提取指定类及其父类上的组件字段元数据。
     *
     * @param type 组件实现类或配置 POJO 类
     * @return 按 {@link ComponentField#order()} 排序后的字段元数据列表
     */
    public static List<ComponentFieldDescriptor> extract(Class<?> type) {
        if (type == null || type == Object.class) {
            return new ArrayList<>();
        }

        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            hierarchy.add(current);
            current = current.getSuperclass();
        }
        // 父类在前，子类在后，保证子类同 key 声明覆盖父类
        Collections.reverse(hierarchy);

        Map<String, ComponentFieldDescriptor> byKey = new LinkedHashMap<>();
        for (Class<?> clazz : hierarchy) {
            for (Field field : clazz.getDeclaredFields()) {
                ComponentField annotation = field.getAnnotation(ComponentField.class);
                if (annotation == null || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                ComponentFieldDescriptor descriptor = toDescriptor(field, annotation);
                byKey.put(descriptor.getKey(), descriptor);
            }
        }

        List<ComponentFieldDescriptor> fields = new ArrayList<>(byKey.values());
        // List.sort 是稳定排序，order 相同时保持父类到子类的声明顺序
        fields.sort((left, right) -> Integer.compare(left.getOrder(), right.getOrder()));
        return fields;
    }

    private static ComponentFieldDescriptor toDescriptor(Field field, ComponentField annotation) {
        String key = StrUtil.isBlank(annotation.key()) ? field.getName() : annotation.key().trim();

        ComponentFieldDescriptor descriptor = new ComponentFieldDescriptor();
        descriptor.setKey(key);
        descriptor.setLabel(StrUtil.isBlank(annotation.label()) ? key : annotation.label());
        descriptor.setType(annotation.type().name());
        descriptor.setGroup(StrUtil.isBlank(annotation.group()) ? "基础配置" : annotation.group());
        descriptor.setOrder(annotation.order());
        descriptor.setRequired(annotation.required());
        descriptor.setPlaceholder(annotation.placeholder());
        descriptor.setHelp(annotation.help());
        descriptor.setPattern(annotation.pattern());
        descriptor.setPatternMessage(annotation.patternMessage());
        descriptor.setDefaultValue(annotation.defaultValue());
        descriptor.setMultiple(annotation.multiple());
        descriptor.setSecret(annotation.secret());
        descriptor.setValueFormat(annotation.valueFormat().name());
        descriptor.setDependsOn(annotation.dependsOn());
        descriptor.setVisibleWhen(Arrays.asList(annotation.visibleWhen()));
        descriptor.setDataSourceType(annotation.dataSourceType());
        descriptor.setDataSourceField(annotation.dataSourceField());
        descriptor.setSchemaField(annotation.schemaField());
        descriptor.setTableField(annotation.tableField());
        descriptor.setCustomComponent(annotation.customComponent());

        if (!Double.isNaN(annotation.min())) {
            descriptor.setMin(annotation.min());
        }
        if (!Double.isNaN(annotation.max())) {
            descriptor.setMax(annotation.max());
        }
        if (annotation.step() != 0) {
            descriptor.setStep(annotation.step());
        }

        for (ComponentFieldOption option : annotation.options()) {
            descriptor
                .getOptions()
                .add(
                    new ComponentFieldOptionDescriptor(
                        option.value(),
                        StrUtil.isBlank(option.label()) ? option.value() : option.label(),
                        option.description()
                    )
                );
        }
        return descriptor;
    }
}
