package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.domain.JobInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WorkerLogForwardServiceTest {

    @Test
    void forwardsWhenClusterModeAndTargetIsAnotherNode() {
        WorkerLogForwardService service = service("master", "10.0.0.1:8080");

        JobInstance instance = instance("10.0.0.2:8080");

        assertThat(service.shouldForward(instance, false)).isTrue();
    }

    @Test
    void doesNotForwardWhenTargetIsCurrentNode() {
        WorkerLogForwardService service = service("master,worker", "10.0.0.1:8080");

        JobInstance instance = instance("http://10.0.0.1:8080/");

        assertThat(service.shouldForward(instance, false)).isFalse();
    }

    @Test
    void doesNotForwardAlreadyForwardedRequest() {
        WorkerLogForwardService service = service("worker", "10.0.0.2:8080");

        JobInstance instance = instance("10.0.0.1:8080");

        assertThat(service.shouldForward(instance, true)).isFalse();
    }

    @Test
    void doesNotForwardInStandaloneMode() {
        WorkerLogForwardService service = service("standalone", "10.0.0.1:8080");

        JobInstance instance = instance("10.0.0.2:8080");

        assertThat(service.shouldForward(instance, false)).isFalse();
    }

    @Test
    void forwardGetCallsWorkerAndForwardsAuthorization() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> forwarded = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/worker/log", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            forwarded.set(exchange.getRequestHeaders().getFirst(WorkerLogForwardService.FORWARDED_HEADER));
            byte[] body = "{\"success\":true,\"content\":\"worker-log\",\"newOffset\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            WorkerLogForwardService service = service("master", "10.0.0.1:8080");
            String workerNode = "127.0.0.1:" + server.getAddress().getPort();

            Map<String, Object> result = service.forwardGet(
                workerNode,
                "/api/worker/log",
                Map.of("jobCode", "job-1", "jobInstanceCode", "instance-1", "offset", "0"),
                "Bearer test-token"
            );

            assertThat(result.get("content")).isEqualTo("worker-log");
            assertThat(result.get("newOffset")).isEqualTo(42);
            assertThat(query.get()).contains("jobCode=job-1").contains("jobInstanceCode=instance-1").contains("offset=0");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            assertThat(forwarded.get()).isEqualTo("1");
        } finally {
            server.stop(0);
        }
    }

    private WorkerLogForwardService service(String mode, String currentNode) {
        DeploymentProperties deploymentProperties = new DeploymentProperties();
        deploymentProperties.setMode(mode);
        return new WorkerLogForwardService(
            deploymentProperties,
            new ObjectMapper(),
            currentNode,
            HttpClient.newHttpClient()
        );
    }

    private JobInstance instance(String execNode) {
        JobInstance instance = new JobInstance();
        instance.setExecNode(execNode);
        return instance;
    }
}
