package com.data.job.component.javascript;

import com.data.job.FlowFile;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * JavaScript引擎 - 支持在Spring Boot RestartClassLoader环境下动态编译和执行用户自定义Java代码
 */
public class JavaScriptEngine {

    private final ClassLoader parentClassLoader;

    private Class<?> compiledClass;
    private Object scriptInstance;

    public JavaScriptEngine() {
        // 使用FlowFile的类加载器作为父类加载器，确保类可见性
        this.parentClassLoader = FlowFile.class.getClassLoader();
    }

    /**
     * 编译用户自定义Java代码
     * @param className 类名
     * @param javaCode Java源代码
     * @return 编译后的类字节码
     * @throws Exception 编译异常
     */
    public byte[] compileScript(String className, String javaCode) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("无法获取Java编译器，请确保在JDK环境中运行");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        MemoryFileManager fileManager = new MemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null), parentClassLoader);

        // 创建源代码对象
        JavaFileObject sourceFile = new MemoryJavaFileObject(className, javaCode);

        List<JavaFileObject> compilationUnits = Arrays.asList(sourceFile);

        // 设置编译选项
        List<String> options = Arrays.asList(
            "-classpath",
            System.getProperty("java.class.path"),
            "-g", // 生成调试信息
            "-parameters" // 保留参数名称
        );

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

        boolean success = task.call();

        // 检查编译诊断信息
        if (!success) {
            StringWriter errorWriter = new StringWriter();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorWriter.write("编译错误: " + diagnostic.getMessage(null) + "\n");
                errorWriter.write("位置: " + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() + "\n");
            }
            throw new CompilationException("Java代码编译失败:\n" + errorWriter.toString());
        }

        // 获取编译后的字节码
        return fileManager.getClassBytes(className);
    }

    /**
     * 执行用户自定义Java代码
     * @param className 类名
     * @param javaCode Java源代码
     * @param flowFile 要处理的FlowFile对象
     * @param context 脚本执行上下文
     * @return 处理后的FlowFile对象
     * @throws Exception 执行异常
     */
    public FlowFile executeScript(String className, String javaCode, FlowFile flowFile, ScriptContext context) throws Exception {
        if (scriptInstance == null) {
            byte[] classBytes = compileScript(className, javaCode);
            // 使用自定义类加载器加载编译后的类
            ScriptClassLoader classLoader = new ScriptClassLoader(parentClassLoader);
            compiledClass = classLoader.loadClass(className, classBytes);
            // 创建脚本实例并执行
            scriptInstance = compiledClass.getDeclaredConstructor().newInstance();
        }

        // 查找并执行process方法
        Method processMethod = compiledClass.getMethod("process", FlowFile.class, Map.class);
        Object result = processMethod.invoke(scriptInstance, flowFile, context.getContext());

        if (result instanceof FlowFile) {
            return (FlowFile) result;
        } else {
            throw new ExecutionException("脚本方法必须返回FlowFile对象");
        }
    }

    /**
     * 编译异常类
     */
    public static class CompilationException extends Exception {

        public CompilationException(String message) {
            super(message);
        }
    }

    /**
     * 执行异常类
     */
    public static class ExecutionException extends Exception {

        public ExecutionException(String message) {
            super(message);
        }
    }

    /**
     * 内存中的Java文件对象 - 用于存储源代码
     */
    private static class MemoryJavaFileObject extends SimpleJavaFileObject {

        private final String code;

        protected MemoryJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * 内存中的类文件对象 - 用于存储编译后的字节码
     */
    private static class MemoryClassFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream;

        protected MemoryClassFileObject(String className) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
            this.outputStream = new ByteArrayOutputStream();
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * 内存文件管理器 - 管理内存中的源文件和类文件
     * <p>
     * 编译时会通过传入的类加载器解析依赖类，而不是依赖 {@code -classpath}。
     * 这样在 Spring Boot 可执行 jar（依赖位于 BOOT-INF/lib、BOOT-INF/classes 中）运行时，
     * 动态编译依然能找到 FlowFile、fastjson2 等类型。
     */
    private static class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        private final Map<String, MemoryClassFileObject> classFiles;
        private final ClasspathIndex classpathIndex;

        protected MemoryFileManager(JavaFileManager fileManager, ClassLoader classLoader) {
            super(fileManager);
            this.classFiles = new HashMap<>();
            this.classpathIndex = new ClasspathIndex(classLoader);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling)
            throws IOException {
            if (kind == JavaFileObject.Kind.CLASS) {
                MemoryClassFileObject classFile = new MemoryClassFileObject(className);
                classFiles.put(className, classFile);
                return classFile;
            }
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }

        @Override
        public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse)
            throws IOException {
            List<JavaFileObject> result = new ArrayList<>();
            Iterable<JavaFileObject> standard = super.list(location, packageName, kinds, recurse);
            if (standard != null) {
                for (JavaFileObject file : standard) {
                    result.add(file);
                }
            }
            // 仅从运行时类加载器补充 CLASS_PATH 下的类，避免覆盖 JDK 平台类
            if (location == StandardLocation.CLASS_PATH && kinds.contains(JavaFileObject.Kind.CLASS)) {
                result.addAll(classpathIndex.list(packageName));
            }
            return result;
        }

        @Override
        public String inferBinaryName(Location location, JavaFileObject file) {
            if (file instanceof ClasspathJavaFileObject) {
                return ((ClasspathJavaFileObject) file).getBinaryName();
            }
            return super.inferBinaryName(location, file);
        }

        @Override
        public boolean isSameFile(FileObject a, FileObject b) {
            if (a instanceof ClasspathJavaFileObject || b instanceof ClasspathJavaFileObject) {
                return a.equals(b);
            }
            return super.isSameFile(a, b);
        }

        public byte[] getClassBytes(String className) {
            MemoryClassFileObject classFile = classFiles.get(className);
            return classFile != null ? classFile.getBytes() : null;
        }
    }

    /**
     * classpath 上的类文件对象，实际的字节码通过运行时类加载器按需读取。
     */
    private static class ClasspathJavaFileObject extends SimpleJavaFileObject {

        private final String binaryName;
        private final ClassLoader classLoader;

        protected ClasspathJavaFileObject(String binaryName, ClassLoader classLoader) {
            super(URI.create("classloader:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
            this.binaryName = binaryName;
            this.classLoader = classLoader;
        }

        public String getBinaryName() {
            return binaryName;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            InputStream input = classLoader.getResourceAsStream(binaryName.replace('.', '/') + Kind.CLASS.extension);
            if (input == null) {
                throw new FileNotFoundException(binaryName);
            }
            return input;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClasspathJavaFileObject)) {
                return false;
            }
            return binaryName.equals(((ClasspathJavaFileObject) obj).binaryName);
        }

        @Override
        public int hashCode() {
            return binaryName.hashCode();
        }
    }

    /**
     * 基于运行时类加载器枚举包内类名，兼容普通目录、jar 以及 Spring Boot 的可执行 jar。
     */
    private static class ClasspathIndex {

        private final ClassLoader classLoader;
        private final Map<String, List<JavaFileObject>> cache = new HashMap<>();

        ClasspathIndex(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        synchronized List<JavaFileObject> list(String packageName) {
            List<JavaFileObject> cached = cache.get(packageName);
            if (cached != null) {
                return cached;
            }
            List<JavaFileObject> files = new ArrayList<>();
            if (packageName != null && !packageName.isEmpty()) {
                String path = packageName.replace('.', '/');
                Set<String> simpleNames = new LinkedHashSet<>();
                collectFromResources(path, simpleNames);
                if (simpleNames.isEmpty()) {
                    // 某些 jar 不包含目录项，退化到直接扫描类加载器 URL
                    collectFromClassLoaderUrls(path, simpleNames);
                }
                for (String simpleName : simpleNames) {
                    files.add(new ClasspathJavaFileObject(packageName + "." + simpleName, classLoader));
                }
            }
            cache.put(packageName, files);
            return files;
        }

        private void collectFromResources(String path, Set<String> simpleNames) {
            try {
                Enumeration<URL> resources = classLoader.getResources(path);
                while (resources.hasMoreElements()) {
                    collectFromUrl(resources.nextElement(), path, simpleNames);
                }
            } catch (IOException e) {
                // 忽略，改用类加载器 URL 扫描
            }
        }

        private void collectFromUrl(URL url, String path, Set<String> simpleNames) {
            try {
                if ("file".equals(url.getProtocol())) {
                    File directory = new File(url.toURI());
                    File[] children = directory.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            if (child.isFile() && child.getName().endsWith(".class")) {
                                simpleNames.add(stripClassSuffix(child.getName()));
                            }
                        }
                    }
                    return;
                }
                URLConnection connection = url.openConnection();
                if (connection instanceof JarURLConnection) {
                    appendJarEntries(((JarURLConnection) connection).getJarFile(), path, simpleNames);
                }
            } catch (Exception e) {
                // 单个 classpath 条目解析失败不影响其它条目
            }
        }

        private void collectFromClassLoaderUrls(String path, Set<String> simpleNames) {
            if (!(classLoader instanceof URLClassLoader)) {
                return;
            }
            for (URL url : ((URLClassLoader) classLoader).getURLs()) {
                try {
                    URLConnection connection = url.openConnection();
                    if (connection instanceof JarURLConnection) {
                        appendJarEntries(((JarURLConnection) connection).getJarFile(), path, simpleNames);
                    }
                } catch (Exception e) {
                    // 忽略无法解析的条目
                }
            }
        }

        private void appendJarEntries(JarFile jarFile, String path, Set<String> simpleNames) {
            String prefix = path + "/";
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(prefix) && name.endsWith(".class")) {
                    String remainder = name.substring(prefix.length());
                    // 只取当前包的类，子包由 javac 按需单独请求
                    if (remainder.indexOf('/') < 0) {
                        simpleNames.add(stripClassSuffix(remainder));
                    }
                }
            }
        }

        private static String stripClassSuffix(String fileName) {
            return fileName.substring(0, fileName.length() - ".class".length());
        }
    }

    /**
     * 脚本类加载器 - 专门用于加载动态编译的类
     * 继承自SecureClassLoader，确保安全性
     */
    private static class ScriptClassLoader extends SecureClassLoader {

        private final Map<String, byte[]> classBytes;

        public ScriptClassLoader(ClassLoader parent) {
            super(parent);
            this.classBytes = new HashMap<>();
        }

        public Class<?> loadClass(String name, byte[] bytes) throws ClassNotFoundException {
            classBytes.put(name, bytes);
            return loadClass(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classBytes.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }
}
