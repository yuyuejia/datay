package com.data.metadata.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义驱动类加载器
 * 用于隔离不同版本的数据库驱动，避免类冲突
 */
public class DriverClassLoader extends URLClassLoader {

    private final Map<String, Class<?>> loadedClasses = new HashMap<>();

    public DriverClassLoader() {
        super(new URL[0], null); // 使用null作为parent，实现类加载器隔离
    }

    /**
     * 回退类加载器：优先使用加载本类的类加载器（Spring Boot 可执行 jar 中为启动类加载器），
     * 其次线程上下文类加载器，最后系统类加载器。避免在可执行 jar 中 {@code BOOT-INF/lib} 下的驱动无法被系统类加载器识别。
     */
    private static ClassLoader fallbackClassLoader() {
        ClassLoader ownClassLoader = DriverClassLoader.class.getClassLoader();
        if (ownClassLoader != null) {
            return ownClassLoader;
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return contextClassLoader != null ? contextClassLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * 添加驱动JAR文件到类路径
     */
    public void addDriverJar(String jarPath) {
        try {
            URL jarUrl = new URL("file:" + jarPath);
            addURL(jarUrl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add driver JAR: " + jarPath, e);
        }
    }

    /**
     * 从已加载的驱动 JAR 中强制加载类（子加载器优先），再回退到应用类加载器。
     *
     * <p>用于通用 JDBC 自定义驱动：驱动类名任意（如 {@code org.apache.hive.jdbc.HiveDriver}），
     * 无法预先登记在 {@link #loadClass(String, boolean)} 的前缀白名单里，因此需要显式从
     * 已加入的 JAR 中加载。
     */
    public Class<?> loadDriverClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }
            try {
                return findClass(name);
            } catch (ClassNotFoundException e) {
                return loadClass(name);
            }
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 检查是否已经加载过
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }

            // JDK / 平台类必须由平台类加载器提供：驱动 JAR 不包含也不应包含这些类，
            // 且 java.sql.Driver 等类型需要与 DriverManager、应用代码保持同一份定义。
            if (isPlatformClass(name)) {
                loadedClass = fallbackClassLoader().loadClass(name);
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }

            // 其余类一律优先从驱动 JAR（隔离加载器）加载，包括驱动自带的 shaded 依赖
            // （如 mssql-jdbc 的 mssql.googlecode.* / microsoft.sql.*）。
            // 这样新增数据库驱动时无需再往白名单里逐个补包名；
            // 只有 JAR 中确实不存在时，才回退到应用类加载器。
            try {
                loadedClass = findClass(name);
                if (resolve) {
                    resolveClass(loadedClass);
                }
                loadedClasses.put(name, loadedClass);
                return loadedClass;
            } catch (ClassNotFoundException e) {
                try {
                    loadedClass = fallbackClassLoader().loadClass(name);
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                } catch (ClassNotFoundException e2) {
                    throw new ClassNotFoundException("Class not found by both custom and fallback class loaders: " + name, e2);
                }
            }
        }
    }

    /**
     * 是否为 JDK / 平台自带的类。
     *
     * <p>这些类必须交给平台类加载器，不能从驱动 JAR 中重复定义：
     * 一方面驱动 JAR 通常不包含它们，另一方面像 {@code java.sql.Driver}、
     * {@code javax.sql.DataSource} 这类核心接口一旦出现两份定义，驱动注册与连接调用会直接失败。
     */
    private static boolean isPlatformClass(String name) {
        return (
            name.startsWith("java.") ||
            name.startsWith("javax.") ||
            name.startsWith("jdk.") ||
            name.startsWith("sun.") ||
            name.startsWith("com.sun.")
        );
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 检查是否已经加载过
        Class<?> loadedClass = loadedClasses.get(name);
        if (loadedClass != null) {
            return loadedClass;
        }

        // 将类名转换为资源路径
        String resourcePath = name.replace('.', '/') + ".class";

        try (InputStream is = getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new ClassNotFoundException("Class not found in custom classloader: " + name);
            }

            // 读取类文件字节码
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            byte[] classBytes = baos.toByteArray();

            // 定义类
            loadedClass = defineClass(name, classBytes, 0, classBytes.length);
            loadedClasses.put(name, loadedClass);

            return loadedClass;
        } catch (IOException e) {
            throw new ClassNotFoundException("Error loading class: " + name, e);
        }
    }

    /**
     * 清理加载的类
     */
    public void cleanup() {
        loadedClasses.clear();
    }
}
