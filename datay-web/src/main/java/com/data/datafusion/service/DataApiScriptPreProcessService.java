package com.data.datafusion.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 「已存在的 API」前置处理脚本执行器。
 *
 * <p>使用 Rhino 执行真正的 JavaScript（与 ETL 模块中动态编译 Java 源码的
 * {@code JavaScriptComponent} 不是同一套实现）。脚本入参为 {@code params} 和
 * {@code utils}，必须返回一个对象，返回对象的键值会合并进调用参数上下文，
 * 随后即可在 URL、请求头、请求体中以 {@code ${name}} 引用。</p>
 *
 * <p>脚本运行在受限沙箱中：只暴露标准 JS 对象和少量工具函数，禁止访问 Java
 * 类、网络与文件系统，并受执行时间与输出大小限制。</p>
 */
@Service
public class DataApiScriptPreProcessService {

    private static final int MAX_SCRIPT_LENGTH = 64 * 1024;

    private static final int MAX_OUTPUT_LENGTH = 1024 * 1024;

    private static final int INSTRUCTION_OBSERVER_THRESHOLD = 10_000;

    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private final long timeoutMs;

    public DataApiScriptPreProcessService(
        @Value("${datay.data-api.pre-process-script-timeout-ms:3000}") long timeoutMs
    ) {
        this.timeoutMs = timeoutMs <= 0 ? 3000 : timeoutMs;
    }

    /**
     * 执行前置脚本，返回需要合并进调用上下文的参数。
     *
     * @param script JavaScript 源码，支持在顶层直接使用 {@code return}。
     * @param params 调用方参数。
     * @return 脚本返回对象展开后的键值对。
     */
    public Map<String, Object> execute(String script, Map<String, Object> params) {
        if (script == null || script.isBlank()) {
            throw new DataApiAccessException(500, "前置脚本不能为空");
        }
        if (script.length() > MAX_SCRIPT_LENGTH) {
            throw new DataApiAccessException(500, "前置脚本过长，最多支持 " + MAX_SCRIPT_LENGTH + " 个字符");
        }
        Map<String, Object> scriptParams = params == null ? Map.of() : params;

        // 包装成 IIFE，让脚本可以像普通函数一样使用 return。
        String wrapped =
            "(function(params, utils) { var __result = (function() {\n" +
            script +
            "\n}).call(null); return __result; }).call(null, params, utils)";

        ContextFactory factory = new DeadlineContextFactory(System.nanoTime() + timeoutMs * 1_000_000L, timeoutMs);
        Context cx = factory.enterContext();
        try {
            cx.setOptimizationLevel(-1);
            cx.setLanguageVersion(Context.VERSION_ES6);
            cx.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD);
            cx.setClassShutter(className -> false);

            Scriptable scope = cx.initSafeStandardObjects();
            NativeObject paramsObject = new NativeObject();
            for (Map.Entry<String, Object> entry : scriptParams.entrySet()) {
                if (entry.getKey() != null) {
                    ScriptableObject.putProperty(paramsObject, entry.getKey(), toScriptValue(entry.getValue()));
                }
            }
            Scriptable utilsObject = createUtils();
            ScriptableObject.putProperty(scope, "params", paramsObject);
            ScriptableObject.putProperty(scope, "utils", utilsObject);

            Object result = cx.evaluateString(scope, wrapped, "data-api-pre-process", 1, null);
            Object jsonValue = NativeJSON.stringify(cx, scope, result, Undefined.instance, Undefined.instance);
            if (jsonValue == null || Undefined.isUndefined(jsonValue)) {
                throw new DataApiAccessException(500, "前置脚本必须返回一个对象");
            }
            String jsonText = Context.toString(jsonValue);
            if (jsonText.length() > MAX_OUTPUT_LENGTH) {
                throw new DataApiAccessException(500, "前置脚本返回结果过大，最多支持 " + MAX_OUTPUT_LENGTH + " 个字符");
            }
            Object parsed = JSON.parse(jsonText);
            if (!(parsed instanceof JSONObject object)) {
                throw new DataApiAccessException(500, "前置脚本必须返回一个对象");
            }
            Map<String, Object> output = new LinkedHashMap<>();
            for (String key : object.keySet()) {
                output.put(key, object.get(key));
            }
            return output;
        } catch (DataApiAccessException e) {
            throw e;
        } catch (RhinoException e) {
            int line = e.lineNumber();
            // 包装脚本在用户脚本前多了一行；语法错误可能落在包装尾行，按脚本行数收敛。
            long scriptLineCount = Math.max(1, script.lines().count());
            int scriptLine = (int) Math.min(scriptLineCount, Math.max(1, line - 1));
            String message = e.details() == null || e.details().isBlank() ? e.getMessage() : e.details();
            throw new DataApiAccessException(500, "前置脚本执行失败（第 " + scriptLine + " 行）：" + message);
        } catch (Exception e) {
            String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
            throw new DataApiAccessException(500, "前置脚本执行失败：" + message);
        } finally {
            Context.exit();
        }
    }

    private static Scriptable createUtils() {
        NativeObject utils = new NativeObject();
        putFunction(utils, "base64Encode", args -> Base64.getEncoder().encodeToString(stringArg(args, 0).getBytes(StandardCharsets.UTF_8)));
        putFunction(utils, "base64Decode", args -> new String(Base64.getDecoder().decode(stringArg(args, 0)), StandardCharsets.UTF_8));
        putFunction(utils, "md5", args -> digestHex("MD5", stringArg(args, 0)));
        putFunction(utils, "sha1", args -> digestHex("SHA-1", stringArg(args, 0)));
        putFunction(utils, "sha256", args -> digestHex("SHA-256", stringArg(args, 0)));
        putFunction(utils, "hmacSha256", args -> hmacSha256(stringArg(args, 0), stringArg(args, 1)));
        putFunction(utils, "urlEncode", args -> URLEncoder.encode(stringArg(args, 0), StandardCharsets.UTF_8));
        putFunction(utils, "uuid", args -> UUID.randomUUID().toString());
        putFunction(utils, "now", args -> (double) System.currentTimeMillis());
        putFunction(utils, "formatDate", args -> formatDate(args));
        return utils;
    }

    private static void putFunction(NativeObject target, String name, ScriptFunction function) {
        target.put(
            name,
            target,
            new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                    return function.apply(args);
                }
            }
        );
    }

    /**
     * 只把字符串、数字、布尔值直接传给脚本；其他结构序列化为 JSON 字符串，避免暴露 Java 对象。
     */
    private static Object toScriptValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return JSON.toJSONString(value);
    }

    private static String stringArg(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null || Undefined.isUndefined(args[index])) {
            return "";
        }
        return Context.toString(args[index]);
    }

    private static String digestHex(String algorithm, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("摘要算法不可用：" + algorithm, e);
        }
    }

    private static String hmacSha256(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String formatDate(Object[] args) {
        long timestamp = args != null && args.length > 0 && !Undefined.isUndefined(args[0])
            ? (long) Context.toNumber(args[0])
            : System.currentTimeMillis();
        String pattern = args != null && args.length > 1 && !Undefined.isUndefined(args[1])
            ? Context.toString(args[1])
            : DEFAULT_DATE_PATTERN;
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
        } catch (Exception e) {
            throw new IllegalArgumentException("时间格式无效：" + pattern);
        }
    }

    /** 在脚本执行超过时限时主动中断，避免死循环拖垮请求线程。 */
    private static final class DeadlineContextFactory extends ContextFactory {

        private final long deadlineNanos;

        private final long timeoutMs;

        private DeadlineContextFactory(long deadlineNanos, long timeoutMs) {
            this.deadlineNanos = deadlineNanos;
            this.timeoutMs = timeoutMs;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            if (System.nanoTime() > deadlineNanos) {
                throw new EvaluatorException("前置脚本执行超时（超过 " + timeoutMs + " ms）");
            }
        }
    }

    @FunctionalInterface
    private interface ScriptFunction {
        Object apply(Object[] args);
    }
}
