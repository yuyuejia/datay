package com.data.datafusion.job.etl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务 JSON 宿主机地址改写器。
 *
 * <p>docker 模式下容器通过默认网络访问宿主机会遇到障碍：容器内的 {@code localhost} / {@code 127.0.0.1}
 * 指向容器自身，无法访问跑在宿主机上的数据库或服务。本类把任务 JSON 中指向回环地址的 URL
 * 改写为可在容器内访问宿主机的主机名（默认 {@code host.docker.internal}），
 * 使用户只切换执行模式即可正常连接本地数据源。</p>
 *
 * <p>仅改写回环地址，远程地址（含局域网、公网）保持不变；解析失败时原样返回。</p>
 */
public final class JobContextHostRewriter {

    /** 匹配 URL 中位于 {@code ://} 之后的回环主机名。 */
    private static final Pattern LOOPBACK_IN_URL = Pattern.compile("://(127\\.0\\.0\\.1|localhost)(?=[:/?#]|$)");

    /** 单独存放主机名的字段。 */
    private static final Set<String> HOST_KEYS = Set.of("hostname", "host");

    private static final String LOOPBACK_V4 = "127.0.0.1";

    private JobContextHostRewriter() {}

    /**
     * 把任务 JSON 中的回环地址改写为指定主机别名。
     *
     * @param jobContext 任务 JSON
     * @param alias      宿主机别名，如 {@code host.docker.internal}
     * @return 改写后的 JSON；入参为空或解析失败时返回原值
     */
    public static String rewriteLoopback(String jobContext, String alias) {
        if (StrUtil.isBlank(jobContext) || StrUtil.isBlank(alias)) {
            return jobContext;
        }
        try {
            Object root = JSON.parse(jobContext);
            rewriteNode(root, alias.trim());
            return JSON.toJSONString(root);
        } catch (Exception e) {
            return jobContext;
        }
    }

    private static void rewriteNode(Object node, String alias) {
        if (node instanceof JSONObject object) {
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text) {
                    String rewritten = rewriteValue(entry.getKey(), text, alias);
                    if (!rewritten.equals(text)) {
                        entry.setValue(rewritten);
                    }
                } else if (value != null) {
                    rewriteNode(value, alias);
                }
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !(item instanceof String)) {
                    rewriteNode(item, alias);
                }
            }
        }
    }

    private static String rewriteValue(String key, String value, String alias) {
        if (key != null && HOST_KEYS.contains(key.toLowerCase()) && isLoopback(value)) {
            return alias;
        }
        Matcher matcher = LOOPBACK_IN_URL.matcher(value);
        if (matcher.find()) {
            return matcher.replaceAll("://" + Matcher.quoteReplacement(alias));
        }
        return value;
    }

    private static boolean isLoopback(String value) {
        return "localhost".equalsIgnoreCase(value.trim()) || LOOPBACK_V4.equals(value.trim());
    }
}
