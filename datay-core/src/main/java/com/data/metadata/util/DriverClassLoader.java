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

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 检查是否已经加载过
        synchronized (getClassLoadingLock(name)) {
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }

            // 如果是驱动相关类，优先使用当前类加载器加载
            if (
                name.startsWith("java.sql.") ||
                name.startsWith("javax.sql.") ||
                name.startsWith("com.mysql.") ||
                name.startsWith("oracle.jdbc.") ||
                name.startsWith("org.postgresql.") ||
                name.startsWith("com.microsoft.sqlserver.") ||
                name.startsWith("org.duckdb.")
            ) {
                // 首先尝试在当前类加载器中查找
                try {
                    loadedClass = findClass(name);
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    loadedClasses.put(name, loadedClass);
                    return loadedClass;
                } catch (ClassNotFoundException e) {
                    // 如果自定义类加载器找不到，回退到应用类加载器
                    try {
                        loadedClass = fallbackClassLoader().loadClass(name);
                        if (resolve) {
                            resolveClass(loadedClass);
                        }
                        return loadedClass;
                    } catch (ClassNotFoundException e2) {
                        // 应用类加载器也找不到，抛出异常
                        throw new ClassNotFoundException("Class not found by both custom and fallback class loaders: " + name, e2);
                    }
                }
            }

            // 对于其他类，使用回退类加载器
            try {
                loadedClass = fallbackClassLoader().loadClass(name);
                return loadedClass;
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundException("Class not found: " + name);
            }
        }
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
