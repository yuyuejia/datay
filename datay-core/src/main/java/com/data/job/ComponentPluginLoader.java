package com.data.job;

import cn.hutool.core.util.StrUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 外部组件 JAR 插件加载器。
 * <p>
 * 启动时从插件目录加载所有 {@code .jar} 文件，扫描其中带有 {@link ComponentRegister} 注解、
 * 且实现了 {@link Component} 的类并注册到 {@link ComponentFactory}。插件目录按以下顺序确定：
 * <ol>
 *     <li>JVM 参数 {@code -Ddatay.component.plugin-dir=/path/to/plugins}</li>
 *     <li>环境变量 {@code DATAY_COMPONENT_PLUGIN_DIR}</li>
 *     <li>默认目录 {@code ${user.dir}/plugins}</li>
 * </ol>
 * 插件仅支持重启生效，不做运行时热插拔。
 */
public final class ComponentPluginLoader {

    /** 插件目录 JVM 参数名。 */
    public static final String PLUGIN_DIR_PROPERTY = "datay.component.plugin-dir";

    /** 插件目录环境变量名。 */
    public static final String PLUGIN_DIR_ENV = "DATAY_COMPONENT_PLUGIN_DIR";

    /** 默认插件目录名称。 */
    private static final String DEFAULT_PLUGIN_DIR = "plugins";

    /** 持有已创建的类加载器，避免被 GC 回收。 */
    private static final List<URLClassLoader> LOADERS = new ArrayList<>();

    private ComponentPluginLoader() {}

    /**
     * 扫描并加载插件目录下的所有组件 JAR。
     *
     * @return 成功注册的组件数量
     */
    public static synchronized int load() {
        File pluginDir = resolvePluginDir();
        if (pluginDir == null || !pluginDir.isDirectory()) {
            return 0;
        }

        File[] jars = pluginDir.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            return 0;
        }
        Arrays.sort(jars, Comparator.comparing(File::getName));

        URLClassLoader classLoader = createClassLoader(jars);
        LOADERS.add(classLoader);

        int registeredCount = 0;
        for (File jar : jars) {
            registeredCount += loadJar(jar, classLoader);
        }

        if (registeredCount > 0) {
            System.out.println("已从插件目录 " + pluginDir.getAbsolutePath() + " 加载 " + registeredCount + " 个 ETL 组件");
        }
        return registeredCount;
    }

    /**
     * 解析插件目录。目录不存在时返回默认目录路径，由调用方判断是否存在。
     *
     * @return 插件目录
     */
    public static File resolvePluginDir() {
        String configured = System.getProperty(PLUGIN_DIR_PROPERTY);
        if (StrUtil.isBlank(configured)) {
            configured = System.getenv(PLUGIN_DIR_ENV);
        }
        if (StrUtil.isBlank(configured)) {
            configured = System.getProperty("user.dir") + File.separator + DEFAULT_PLUGIN_DIR;
        }
        return new File(configured.trim());
    }

    private static URLClassLoader createClassLoader(File[] jars) {
        List<URL> urls = new ArrayList<>(jars.length);
        for (File jar : jars) {
            try {
                urls.add(jar.toURI().toURL());
            } catch (IOException e) {
                System.err.println("忽略无法读取的插件 JAR: " + jar.getAbsolutePath() + ", " + e.getMessage());
            }
        }
        return new URLClassLoader(urls.toArray(new URL[0]), ComponentPluginLoader.class.getClassLoader());
    }

    private static int loadJar(File jar, URLClassLoader classLoader) {
        int registeredCount = 0;
        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (!entryName.endsWith(".class") || entryName.startsWith("META-INF/") || entryName.contains("$")) {
                    continue;
                }
                String className = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
                if (isRegisterCandidate(className, classLoader)) {
                    registeredCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("读取插件 JAR 失败: " + jar.getAbsolutePath() + ", " + e.getMessage());
        }
        return registeredCount;
    }

    private static boolean isRegisterCandidate(String className, URLClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            ComponentRegister annotation = clazz.getAnnotation(ComponentRegister.class);
            if (annotation == null || !Component.class.isAssignableFrom(clazz) || java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                return false;
            }
            String code = StrUtil.isBlank(annotation.value()) ? clazz.getSimpleName() : annotation.value();
            ComponentFactory.registerComponent(code, clazz, annotation);
            return true;
        } catch (Throwable e) {
            // 插件中可能包含依赖缺失或当前 JDK 不兼容的类，跳过即可，不影响其他组件注册
            return false;
        }
    }
}
