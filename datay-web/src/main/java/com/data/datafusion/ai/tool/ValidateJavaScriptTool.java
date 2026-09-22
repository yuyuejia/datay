package com.data.datafusion.ai.tool;

import com.data.job.component.javascript.JavaScriptEngine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Java 脚本编译校验工具（只读）。
 *
 * <p>复用 JavaScriptComponent 的运行时编译能力，让模型在给出最终脚本前先做一次真实编译，
 * 借助 javac 的诊断信息快速发现语法、导入或方法签名错误并自我修正。
 */
@Component
public class ValidateJavaScriptTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "validate_javascript";

    /** 组件约定脚本中的公开类名。 */
    public static final String CLASS_NAME = "UserScript";

    private static final Logger LOG = LoggerFactory.getLogger(ValidateJavaScriptTool.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            校验一段 JavaScriptComponent 脚本能否被成功编译。工具不会执行脚本，也不会产生任何副作用：
            - 脚本必须声明 public class UserScript，并实现 process(FlowFile, Map) 方法；
            - 编译成功返回 valid=true；失败返回 valid=false 及 javac 的行列号诊断信息。
            请在给出最终脚本前调用本工具自检，若校验失败请依据报错修改后重试。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "待校验的完整 Java 脚本源码");
        props.put("code", code);
        schema.put("properties", props);
        schema.put("required", List.of("code"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String code = arguments.get("code") == null ? null : String.valueOf(arguments.get("code"));

        Map<String, Object> result = new LinkedHashMap<>();
        if (code == null || code.isBlank()) {
            result.put("valid", false);
            result.put("error", "脚本源码不能为空");
            return result;
        }

        try {
            new JavaScriptEngine().compileScript(CLASS_NAME, code);
            result.put("valid", true);
            result.put("message", "脚本编译通过");
        } catch (Exception e) {
            LOG.debug("AI tool validate_javascript rejected script: {}", e.getMessage());
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}
