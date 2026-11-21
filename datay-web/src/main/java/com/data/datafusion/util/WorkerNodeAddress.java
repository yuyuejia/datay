package com.data.datafusion.util;

import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.config.WorkerExecutionProperties;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * 执行节点地址解析。
 *
 * <p>worker 上报给 master 的 {@code execNode} 由本机 IP 与 {@code server.port} 组成，master 依赖该地址
 * 转发日志请求。多网卡服务器上 {@code InetAddress.getLocalHost()} 可能返回 docker0、网桥等内部地址，
 * 导致其他节点不可达，因此这里统一按以下规则解析：</p>
 *
 * <ol>
 *     <li>优先使用 {@code development.worker.ip} 显式绑定的 IP/主机名；</li>
 *     <li>未绑定时遍历本机网卡，排除 docker / br- / veth / virbr / cni / flannel 等内部网卡，</li>
 *     <li>优先选择 IPv4 私网地址，其次其他非回环、非链路本地地址；</li>
 *     <li>没有可用地址时回退到 {@code InetAddress.getLocalHost()}，最后回退 {@code 127.0.0.1}。</li>
 * </ol>
 */
public final class WorkerNodeAddress {

    private static final Logger LOG = LoggerFactory.getLogger(WorkerNodeAddress.class);

    /** server.port 未配置时的默认端口。 */
    private static final String DEFAULT_PORT = "8080";

    /** 容器、网桥、虚拟化等内部网络常见的网卡名前缀（小写匹配）。 */
    private static final List<String> INTERNAL_INTERFACE_PREFIXES = List.of(
        "docker",
        "br-",
        "bridge",
        "veth",
        "virbr",
        "cni",
        "flannel",
        "cali",
        "tunl",
        "weave",
        "kube",
        "podman",
        "vmnet",
        "vboxnet"
    );

    private static final Object LOCK = new Object();

    private static volatile String detectedIp;

    private static volatile String currentExecNode;

    private WorkerNodeAddress() {}

    /**
     * 当前执行节点地址，格式为 {@code host:port}，IPv6 地址会自动加方括号。
     */
    public static String currentExecNode() {
        String cached = currentExecNode;
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            if (currentExecNode == null) {
                String node = formatHost(resolveIp()) + ":" + resolvePort();
                // Spring 环境尚未就绪时（如过早触发静态初始化）不缓存，避免把默认端口固化。
                if (SpringUtil.getEnvironment() != null) {
                    currentExecNode = node;
                    LOG.info("执行节点地址: {}", node);
                }
                return node;
            }
            return currentExecNode;
        }
    }

    /**
     * 解析执行节点应上报的本机 IP/主机名。
     */
    public static String resolveIp() {
        return resolveIp(configuredIp());
    }

    /**
     * 按指定绑定值解析执行节点 IP，绑定值为空时自动探测；便于测试与复用。
     */
    static String resolveIp(String configuredIp) {
        if (StringUtils.hasText(configuredIp)) {
            return configuredIp.trim();
        }

        String cached = detectedIp;
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            if (detectedIp == null) {
                detectedIp = detectIp();
            }
            return detectedIp;
        }
    }

    private static String configuredIp() {
        WorkerExecutionProperties properties = WorkerExecutionProperties.get();
        return properties == null ? null : properties.getIp();
    }

    private static String detectIp() {
        List<Candidate> candidates = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            LOG.warn("枚举本机网卡失败，回退到 InetAddress.getLocalHost() 解析执行节点 IP", e);
            return fallbackIp();
        }
        if (interfaces == null) {
            return fallbackIp();
        }

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            String name = networkInterface.getName();
            if (isInternalInterface(name)) {
                LOG.debug("跳过内部网卡: {}", name);
                continue;
            }
            try {
                boolean internal =
                    !networkInterface.isUp() ||
                    networkInterface.isLoopback() ||
                    networkInterface.isVirtual() ||
                    networkInterface.isPointToPoint();
                if (internal) {
                    continue;
                }
            } catch (SocketException e) {
                LOG.debug("读取网卡状态失败，跳过: {}", name, e);
                continue;
            }

            int index = networkInterface.getIndex();
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress address = interfaceAddress == null ? null : interfaceAddress.getAddress();
                if (!isUsableAddress(address)) {
                    continue;
                }
                candidates.add(new Candidate(index, name, address));
            }
        }

        if (candidates.isEmpty()) {
            LOG.warn("未发现可用的外部网卡地址，回退到 InetAddress.getLocalHost() 解析执行节点 IP");
            return fallbackIp();
        }

        candidates.sort(
            Comparator.comparing((Candidate candidate) -> candidate.address instanceof Inet4Address ? 0 : 1)
                .thenComparing(candidate -> candidate.address.isSiteLocalAddress() ? 0 : 1)
                .thenComparingInt(candidate -> candidate.index)
                .thenComparing(candidate -> candidate.name)
        );
        Candidate selected = candidates.get(0);
        LOG.info(
            "执行节点 IP 自动解析结果: {} (网卡 {}), 候选地址: {}",
            selected.address.getHostAddress(),
            selected.name,
            candidates.stream().map(candidate -> candidate.name + "=" + candidate.address.getHostAddress()).toList()
        );
        return formatAddress(selected.address);
    }

    private static String fallbackIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            if (isUsableAddress(localHost)) {
                return formatAddress(localHost);
            }
        } catch (UnknownHostException e) {
            LOG.warn("无法通过 InetAddress.getLocalHost() 获取本机地址，使用 127.0.0.1", e);
        }
        return "127.0.0.1";
    }

    /**
     * 是否属于 docker、网桥、容器网络等不应上报的内部网卡。
     */
    static boolean isInternalInterface(String name) {
        if (!StringUtils.hasText(name)) {
            return true;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.equals("lo") || normalized.matches("lo\\d+")) {
            return true;
        }
        for (String prefix : INTERNAL_INTERFACE_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 地址是否可作为执行节点地址：排除回环、链路本地、组播与通配地址。
     */
    static boolean isUsableAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        return (
            !address.isAnyLocalAddress() &&
            !address.isLoopbackAddress() &&
            !address.isLinkLocalAddress() &&
            !address.isMulticastAddress()
        );
    }

    private static String resolvePort() {
        Environment environment = SpringUtil.getEnvironment();
        String port = environment == null ? null : environment.getProperty("server.port");
        if (!StringUtils.hasText(port)) {
            return DEFAULT_PORT;
        }
        String trimmed = port.trim();
        return "0".equals(trimmed) ? DEFAULT_PORT : trimmed;
    }

    static String formatAddress(InetAddress address) {
        return formatHost(address.getHostAddress());
    }

    private static String formatHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "127.0.0.1";
        }
        String trimmed = host.trim();
        if (trimmed.startsWith("[") || trimmed.indexOf(':') < 0) {
            return trimmed;
        }
        return "[" + trimmed + "]";
    }

    private record Candidate(int index, String name, InetAddress address) {}
}
