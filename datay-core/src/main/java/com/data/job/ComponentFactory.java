package com.data.job;

import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import java.util.HashMap;
import java.util.Map;

public class ComponentFactory {

    public static Map<String, String> components = new HashMap<>();

    static {
        // 保留现有的手动注册作为备用
        registerDefaultComponents();
        // 尝试自动扫描和注册带有ETLComponent注解的组件
        try {
            scanAndRegisterComponents();
        } catch (Exception e) {
            System.err.println("自动扫描组件失败，使用默认注册: " + e.getMessage());
        }
    }

    /**
     * 注册默认组件
     */
    private static void registerDefaultComponents() {
        components.put("StreamJdbcInput", "com.data.job.component.StreamJdbcInput");
        components.put("Channel", "com.data.job.component.Channel");
        components.put("StreamSqlUnit", "com.data.job.component.StreamSqlUnit");
        components.put("DuckDBWrite", "com.data.job.component.DuckDBWrite");
        components.put("DuckDBSql", "com.data.job.component.DuckDBSql");
        components.put("SqlInput", "com.data.job.component.SqlInput");
        components.put("DuckDBRegister", "com.data.job.component.DuckDBRegister");
        components.put("JavaScriptComponent", "com.data.job.component.javascript.JavaScriptComponent");
        components.put("GenerateFlowFile", "com.data.job.component.GenerateFlowFile");
        components.put("LogFlowFile", "com.data.job.component.LogFlowFile");
        components.put("MySQLBinlogInput", "com.data.job.component.cdc.MySQLBinlogInput");
        components.put("DuckLakeWrite", "com.data.job.component.DuckLakeWrite");
        components.put("HashRouter", "com.data.job.component.router.HashRouter");
        components.put("RandomRouter", "com.data.job.component.router.RandomRouter");
    }

    /**
     * 扫描并注册带有ETLComponent注解的组件
     */
    private static void scanAndRegisterComponents() {
        // 使用Hutool的ClassUtil扫描com.data.job.etl包下的所有类
        // 注意：这里假设Hutool的ClassUtil可用，根据代码中已有的cn.hutool.core.bean.BeanUtil判断项目已引入Hutool
        String packageName = "com.data.job.component";
        for (Class<?> clazz : ClassUtil.scanPackage(packageName)) {
            // 检查类是否有ETLComponent注解
            ComponentRegister annotation = clazz.getAnnotation(ComponentRegister.class);
            if (annotation != null && Component.class.isAssignableFrom(clazz)) {
                // 获取组件名称
                String componentName = annotation.value();
                if (StrUtil.isBlank(componentName)) {
                    // 如果注解没有指定名称，使用类名
                    componentName = clazz.getSimpleName();
                }
                // 注册组件
                components.put(componentName, clazz.getName());
            }
        }
    }

    /**
     * 添加组件注册方法，允许运行时手动注册组件
     */
    public static void registerComponent(String componentName, String className) {
        components.put(componentName, className);
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