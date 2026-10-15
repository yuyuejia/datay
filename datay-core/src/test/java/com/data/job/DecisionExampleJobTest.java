package com.data.job;

import cn.hutool.core.io.FileUtil;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 决策模型示例任务（{@code docs/example/decision-ollama.json}）的端到端校验。
 *
 * <p>需要本地启动 Ollama（0.35+）并已拉取决策模型（默认 {@code tev1:0.8b}）。
 * 未启动或未拉取模型时自动跳过，不影响常规回归。
 */
public class DecisionExampleJobTest {

    private static final String ROOT = System.getProperty("datay.test.decision.root", "http://localhost:11434");

    private static String resolveJobFile() {
        String relative = "docs/example/decision-ollama.json";
        java.io.File dir = new java.io.File(System.getProperty("basedir", System.getProperty("user.dir")));
        for (int i = 0; i < 4 && dir != null; i++) {
            java.io.File candidate = new java.io.File(dir, relative);
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        return relative;
    }

    @BeforeAll
    static void checkOllama() {
        try {
            String tags = httpGet(ROOT + "/api/tags");
            Assumptions.assumeTrue(tags.contains("tev1"), "未找到决策模型 tev1，跳过示例任务校验");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Ollama 未启动，跳过示例任务校验：" + e.getMessage());
        }
    }

    @Test
    void shouldRunDecisionExampleJob() throws Exception {
        String jobFile = resolveJobFile();
        Assumptions.assumeTrue(FileUtil.exist(jobFile), "示例任务文件不存在：" + jobFile);
        String job = FileUtil.readUtf8String(jobFile);
        new ETLFlowTask().runJob(job);
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(1500);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
