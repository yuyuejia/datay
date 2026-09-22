package com.data.datafusion.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaCodeExtractorTest {

    private static final String SCRIPT = """
        import com.data.job.FlowFile;
        import java.util.Map;

        public class UserScript {
            public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
                return flowFile;
            }
        }
        """;

    @Test
    void extractsJavaFencedBlock() {
        String content = "下面是脚本：\n\n```java\n" + SCRIPT + "```\n说明：直接使用即可。";

        assertThat(JavaCodeExtractor.extract(content)).isEqualTo(SCRIPT.strip());
    }

    @Test
    void skipsNonJavaFenceBeforeJavaFence() {
        String content = "先看结构：\n\n```text\nnot code\n```\n\n```java\n" + SCRIPT + "```";

        assertThat(JavaCodeExtractor.extract(content)).isEqualTo(SCRIPT.strip());
    }

    @Test
    void extractsBareScriptWhenNoFence() {
        String content = "生成的脚本如下：\n" + SCRIPT + "\n以上脚本已完成处理。";

        String code = JavaCodeExtractor.extract(content);

        assertThat(code).startsWith("import com.data.job.FlowFile;");
        assertThat(code).endsWith("}");
        assertThat(code).doesNotContain("生成的脚本如下");
        assertThat(code).doesNotContain("以上脚本已完成处理");
    }

    @Test
    void returnsNullWhenNoJavaScriptPresent() {
        assertThat(JavaCodeExtractor.extract("这里没有代码")).isNull();
        assertThat(JavaCodeExtractor.extract("```sql\nSELECT 1\n```")).isNull();
        assertThat(JavaCodeExtractor.extract(null)).isNull();
    }
}
