package com.data.job;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ETL 组件工厂。
 * <p>
 * 启动时自动扫描 {@code com.data.job.component} 包（含子包）下所有带有
 * {@link ComponentRegister} 注解的组件实现，注册组件编码到实现类的映射，
 * 同时收集组件的名称、分类、描述等元数据，供前端组件面板自动发现使用。
 */
public class ComponentFactory {

    /** 组件编码 -> 实现类全限定名。 */
    public static final Map<String, String> components = new LinkedHashMap<>();

    /** 组件编码 -> 组件元数据。 */
    private static final Map<String, ComponentDescriptor> descriptors = new LinkedHashMap<>();

    /** 组件扫描包。 */
    private static final String COMPONENT_PACKAGE = "com.data.job.component";

    static {
        try {
            scanAndRegisterComponents();
        } catch (Exception e) {
            System.err.println("自动扫描 ETL 组件失败: " + e.getMessage());
        }
    }

    /**
     * 扫描并注册带有 {@link ComponentRegister} 注解的组件（含子包）。
     */
    private static void scanAndRegisterComponents() {
        for (Class<?> clazz : ClassUtil.scanPackage(COMPONENT_PACKAGE)) {
            ComponentRegister annotation = clazz.getAnnotation(ComponentRegister.class);
            if (annotation == null || !Component.class.isAssignableFrom(clazz) || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            String code = StrUtil.isBlank(annotation.value()) ? clazz.getSimpleName() : annotation.value();
            String name = StrUtil.isBlank(annotation.name()) ? code : annotation.name();
            components.put(code, clazz.getName());
            descriptors.put(code, new ComponentDescriptor(code, name, annotation.group(), annotation.desc(), clazz.getName(), annotation.order()));
        }
    }

    /**
     * 添加组件注册方法，允许运行时手动注册组件。
     */
    public static void registerComponent(String componentName, String className) {
        components.put(componentName, className);
    }

    /**
     * 获取所有自动发现的组件元数据，按分组及排序值排列。
     *
     * @return 组件元数据列表
     */
    public static List<ComponentDescriptor> listDescriptors() {
        List<ComponentDescriptor> list = new ArrayList<>(descriptors.values());
        list.sort(Comparator.comparing(ComponentDescriptor::getGroup).thenComparingInt(ComponentDescriptor::getOrder));
        return list;
    }

    /**
     * 获取指定编码的组件元数据。
     *
     * @param code 组件编码
     * @return 组件元数据，不存在时返回 {@code null}
     */
    public static ComponentDescriptor getDescriptor(String code) {
        return descriptors.get(code);
    }

    // 新增支持直接传递类名和参数的构造方法
    public static Component create(String className, Map<String, Object> params) {
        try {
            Class<?> clazz = Class.forName(components.get(className));
            Component instance = (Component) clazz.getDeclaredConstructor().newInstance();

            // 使用Hutool进行Bean注入
            cn.hutool.core.bean.BeanUtil.fillBeanWithMapIgnoreCase(params, instance, true);

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("组件初始化失败: " + className, e);
        }
    }
}
