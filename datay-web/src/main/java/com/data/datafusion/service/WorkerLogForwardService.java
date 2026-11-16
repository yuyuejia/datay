package com.data.datafusion.service;

import cn.hutool.core.net.NetUtil;
import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.config.SkipTenantFilter;
import com.data.datafusion.domain.JobInstance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 任务日志跨节点转发服务。
 *
 * <p>master / worker 分离部署时，日志文件只存在于实际执行任务的 worker 节点。
 * master 收到日志请求后，根据任务实例的 {@code execNode} 把请求转发到对应 worker；
 * 单机部署、未记录执行节点或目标就是当前节点时，仍由本机读取。</p>
 */
@Service
@SkipTenantFilter
public class WorkerLogForwardService {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerLogForwardService.class);

    /** 标记请求已经由其他节点转发而来，避免节点地址判断异常时产生循环转发。 */
    public static final String FORWARDED_HEADER = "X-Datay-Log-Forwarded";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final DeploymentProperties deploymentProperties;

    private final ObjectMapper objectMapper;

    private final String currentExecNode;

    private final HttpClient httpClient;

    @Autowired
    public WorkerLogForwardService(
        DeploymentProperties deploymentProperties,
        ObjectMapper objectMapper,
        @Value("${server.port:8080}") String serverPort
    ) {
        this(deploymentProperties, objectMapper, resolveCurrentExecNode(serverPort), HTTP_CLIENT);
    }

    WorkerLogForwardService(
        DeploymentProperties deploymentProperties,
        ObjectMapper objectMapper,
        String currentExecNode,
        HttpClient httpClient
    ) {
        this.deploymentProperties = deploymentProperties;
        this.objectMapper = objectMapper;
        this.currentExecNode = currentExecNode;
        this.httpClient = httpClient;
    }

    /**
     * 是否需要把日志请求转发到其他执行节点。
     *
     * @param jobInstance 任务实例，包含执行节点地址
     * @param forwardedRequest 当前请求是否由其他节点转发而来
     */
    public boolean shouldForward(JobInstance jobInstance, boolean forwardedRequest) {
        if (forwardedRequest || jobInstance == null || deploymentProperties == null || !deploymentProperties.isClusterMode()) {
            return false;
        }
        String execNode = jobInstance.getExecNode();
        if (!StringUtils.hasText(execNode)) {
            return false;
        }
        return !isCurrentNode(execNode);
    }

    /**
     * 以 GET 方式请求执行节点的日志接口，并解析返回的 JSON。
     */
    public Map<String, Object> forwardGet(
        String execNode,
        String path,
        Map<String, String> queryParams,
        String authorization
    ) throws IOException {
        if (!StringUtils.hasText(execNode)) {
            throw new IOException("任务实例缺少执行节点地址");
        }
        URI uri = buildUri(execNode, path, queryParams);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header(HttpHeaders.ACCEPT, "application/json")
            .header(HttpHeaders.ACCEPT_ENCODING, "identity")
            .header(FORWARDED_HEADER, "1")
            .GET();
        if (StringUtils.hasText(authorization)) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }

        LOG.debug("转发任务日志请求: {} -> {}", path, uri);
        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("转发任务日志请求被中断", e);
        }

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("执行节点返回 HTTP " + statusCode + ": " + abbreviate(response.body()));
        }
        String body = response.body();
        if (!StringUtils.hasText(body)) {
            throw new IOException("执行节点返回了空响应");
        }
        try {
            Map<String, Object> result = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            if (result == null) {
                throw new IOException("执行节点返回了空日志数据");
            }
            return result;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("执行节点返回的日志数据格式错误", e);
        }
    }

    private URI buildUri(String execNode, String path, Map<String, String> queryParams) {
        String baseUrl = execNode.trim();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "http://" + baseUrl;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).path(path.startsWith("/") ? path : "/" + path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (StringUtils.hasText(value)) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build().encode().toUri();
    }

    private boolean isCurrentNode(String execNode) {
        if (!StringUtils.hasText(currentExecNode)) {
            return false;
        }
        return normalizeNode(currentExecNode).equalsIgnoreCase(normalizeNode(execNode));
    }

    private static String normalizeNode(String node) {
        String normalized = node == null ? "" : node.trim();
        if (normalized.startsWith("http://")) {
            normalized = normalized.substring("http://".length());
        } else if (normalized.startsWith("https://")) {
            normalized = normalized.substring("https://".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String resolveCurrentExecNode(String serverPort) {
        if (!StringUtils.hasText(serverPort)) {
            return null;
        }
        return NetUtil.getLocalhostStr() + ":" + serverPort;
    }

    private static String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}
