package com.data.job.component.javascript;

import com.data.job.FlowFile;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.SecureClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        MemoryFileManager fileManager = new MemoryFileManager(compiler.getStandardFileManager(diagnostics, null, null));

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
     */
    private static class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {

        private final Map<String, MemoryClassFileObject> classFiles;

        protected MemoryFileManager(JavaFileManager fileManager) {
            super(fileManager);
            this.classFiles = new HashMap<>();
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

        public byte[] getClassBytes(String className) {
            MemoryClassFileObject classFile = classFiles.get(className);
            return classFile != null ? classFile.getBytes() : null;
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
