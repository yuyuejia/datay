package com.data.datafusion.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从模型回复中抽取最终 Java 代码。
 *
 * <p>模型输出并不总是规整的 markdown，因此按「```java 围栏代码块 → 任意围栏代码块 → 裸代码」
 * 的优先级兜底解析，保证前端始终能拿到可直接落进编辑器的完整脚本。
 *
 * <p>脚本约定为单个 {@code UserScript} 类，裸代码兜底时会从首个 {@code import}/{@code public class}
 * 截取到最后一个右花括号，尽量剔除模型附带的自然语言说明。
 */
public final class JavaCodeExtractor {

    /** 匹配 ```java ... ``` 围栏代码块。 */
    private static final Pattern JAVA_FENCE = Pattern.compile("```(?:java|Java|JAVA)[ \t]*\n?(.*?)```", Pattern.DOTALL);

    /** 匹配任意 ``` ... ``` 围栏代码块，作为未标注语言时的兜底。 */
    private static final Pattern ANY_FENCE = Pattern.compile("```[ \t]*\n?(.*?)```", Pattern.DOTALL);

    /** 裸代码起点：import 语句或类声明。 */
    private static final Pattern CODE_START = Pattern.compile("(?m)^[ \t]*(?:package\\s+[\\w.]+\\s*;|import\\s+[\\w.*]+\\s*;|public\\s+(?:final\\s+)?class\\s+\\w+)");

    private JavaCodeExtractor() {}

    /**
     * 抽取 Java 代码，抽取失败时返回 null。
     */
    public static String extract(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String fenced = firstJavaFence(JAVA_FENCE, content);
        if (fenced == null) {
            fenced = firstJavaFence(ANY_FENCE, content);
        }
        if (fenced != null) {
            return fenced.strip();
        }

        String bare = extractBare(content);
        return looksLikeJava(bare) ? bare.strip() : null;
    }

    /**
     * 判断文本是否像一段 Java 脚本：包含类声明与 process 方法契约。
     */
    public static boolean looksLikeJava(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.contains("class") && text.contains("process") && text.contains("FlowFile");
    }

    /**
     * 返回首个满足 Java 特征（类声明 + process 方法 + FlowFile）的围栏代码块，
     * 跳过模型在脚本之前附带的非 Java 代码块。
     */
    private static String firstJavaFence(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (looksLikeJava(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 无围栏时的兜底：从首个 package/import/class 声明截取到最后一个右花括号。
     */
    private static String extractBare(String content) {
        Matcher matcher = CODE_START.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.start();
        int end = content.lastIndexOf('}');
        if (end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }
}
