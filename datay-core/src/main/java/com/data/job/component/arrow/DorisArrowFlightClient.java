package com.data.job.component.arrow;

import org.apache.arrow.flight.CallOption;
import org.apache.arrow.flight.FlightClient;
import org.apache.arrow.flight.FlightEndpoint;
import org.apache.arrow.flight.FlightInfo;
import org.apache.arrow.flight.FlightStream;
import org.apache.arrow.flight.Location;
import org.apache.arrow.flight.grpc.CredentialCallOption;
import org.apache.arrow.flight.sql.FlightSqlClient;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.util.List;
import java.util.Optional;

/**
 * Doris Arrow Flight SQL 客户端。
 *
 * <p>Doris 的 Arrow Flight SQL 查询分两步：先连接 FE 执行 SQL，拿到 {@link FlightInfo}；
 * 然后按 endpoint 中的 BE 地址建立新的 Flight 连接，用 ticket 拉取数据流。因此不能只复用
 * FE 连接读取结果。</p>
 */
public final class DorisArrowFlightClient implements AutoCloseable {

    /** 每个 Arrow RecordBatch 的回调；返回 false 表示停止读取后续批次。 */
    @FunctionalInterface
    public interface BatchConsumer {
        boolean accept(VectorSchemaRoot root) throws Exception;
    }

    private final BufferAllocator allocator;
    private final FlightClient client;
    private final FlightSqlClient sqlClient;
    private final CredentialCallOption credential;
    private final Location feLocation;
    private final long maxMemoryBytes;
    private final boolean useEncryption;

    private DorisArrowFlightClient(
        BufferAllocator allocator,
        FlightClient client,
        FlightSqlClient sqlClient,
        CredentialCallOption credential,
        Location feLocation,
        long maxMemoryBytes,
        boolean useEncryption
    ) {
        this.allocator = allocator;
        this.client = client;
        this.sqlClient = sqlClient;
        this.credential = credential;
        this.feLocation = feLocation;
        this.maxMemoryBytes = maxMemoryBytes;
        this.useEncryption = useEncryption;
    }

    /**
     * 建立到 Doris FE Arrow Flight SQL 服务的连接。
     *
     * @param host            FE 主机
     * @param port            FE arrow_flight_sql_port
     * @param username        Doris 用户名
     * @param password        Doris 密码
     * @param useEncryption   是否使用 TLS
     * @param maxMemoryBytes  Arrow 内存上限，<=0 时使用默认值
     */
    public static DorisArrowFlightClient connect(
        String host,
        int port,
        String username,
        String password,
        boolean useEncryption,
        long maxMemoryBytes
    ) throws Exception {
        long limit = maxMemoryBytes > 0 ? maxMemoryBytes : 512L * 1024 * 1024;
        BufferAllocator allocator = null;
        FlightClient client = null;
        try {
            allocator = new RootAllocator(limit);
            Location location = useEncryption ? Location.forGrpcTls(host, port) : Location.forGrpcInsecure(host, port);
            client = FlightClient.builder(allocator, location).build();
            FlightSqlClient sqlClient = new FlightSqlClient(client);

            CredentialCallOption credential = null;
            if (username != null && !username.trim().isEmpty()) {
                Optional<CredentialCallOption> token = client.authenticateBasicToken(username, password == null ? "" : password);
                credential = token.orElse(null);
            }
            return new DorisArrowFlightClient(allocator, client, sqlClient, credential, location, limit, useEncryption);
        } catch (Throwable e) {
            closeQuietly(client, allocator);
            if (e instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException(arrowMemoryHint(e), e);
        }
    }

    /**
     * 执行查询并逐批回调结果。
     *
     * <p>Doris 使用 prepared statement 方式执行查询；返回的 endpoint 指向 BE，需要按
     * endpoint 的 Location 单独建立连接读取数据。</p>
     */
    public void query(String sql, BatchConsumer consumer) throws Exception {
        CallOption[] options = callOptions();
        FlightSqlClient.PreparedStatement statement = sqlClient.prepare(sql, options);
        try {
            FlightInfo info = statement.execute(options);
            List<FlightEndpoint> endpoints = info.getEndpoints();
            for (FlightEndpoint endpoint : endpoints) {
                Location location = endpoint.getLocations().isEmpty()
                    ? feLocation
                    : endpoint.getLocations().get(0);
                if (useEncryption) {
                    location = toTlsLocation(location);
                }
                if (!readEndpoint(location, endpoint.getTicket(), options, consumer)) {
                    return;
                }
            }
        } finally {
            statement.close(options);
        }
    }

    private boolean readEndpoint(
        Location location,
        org.apache.arrow.flight.Ticket ticket,
        CallOption[] options,
        BatchConsumer consumer
    ) throws Exception {
        try (
            BufferAllocator streamAllocator = new RootAllocator(maxMemoryBytes);
            FlightClient streamClient = FlightClient.builder(streamAllocator, location).build();
            FlightStream stream = streamClient.getStream(ticket, options)
        ) {
            while (stream.next()) {
                if (!consumer.accept(stream.getRoot())) {
                    return false;
                }
            }
        }
        return true;
    }

    private CallOption[] callOptions() {
        return credential == null ? new CallOption[0] : new CallOption[] { credential };
    }

    /**
     * 确保 BE endpoint 使用 TLS 连接；Doris 返回的 endpoint 可能仍是 grpc scheme。
     */
    private Location toTlsLocation(Location location) {
        java.net.URI uri = location.getUri();
        if (uri == null || uri.getScheme() == null || uri.getScheme().toLowerCase(java.util.Locale.ROOT).contains("tls")) {
            return location;
        }
        if (uri.getHost() == null || uri.getPort() <= 0) {
            return location;
        }
        return Location.forGrpcTls(uri.getHost(), uri.getPort());
    }

    @Override
    public void close() {
        closeQuietly(client, allocator);
    }

    private static void closeQuietly(FlightClient client, BufferAllocator allocator) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // 忽略关闭异常，避免掩盖真实读取错误
            }
        }
        if (allocator != null) {
            try {
                allocator.close();
            } catch (Exception ignored) {
                // 忽略关闭异常
            }
        }
    }

    private static String arrowMemoryHint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (
                current instanceof ExceptionInInitializerError ||
                current instanceof NoClassDefFoundError ||
                (message != null && message.contains("java.nio"))
            ) {
                return "Arrow Flight SQL 初始化失败，请为 JVM 增加参数 --add-opens=java.base/java.nio=ALL-UNNAMED（原始错误：" + error + "）";
            }
            current = current.getCause();
        }
        return "Arrow Flight SQL 初始化失败：" + error;
    }
}
