package com.data.datafusion.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class WorkerNodeAddressTest {

    @Test
    void configuredIpTakesPriority() {
        assertThat(WorkerNodeAddress.resolveIp(" 10.1.2.3 ")).isEqualTo("10.1.2.3");
        assertThat(WorkerNodeAddress.resolveIp("worker-host.internal")).isEqualTo("worker-host.internal");
    }

    @Test
    void blankConfiguredIpFallsBackToDetectedAddress() throws Exception {
        String ip = WorkerNodeAddress.resolveIp(" ");

        assertThat(ip).isNotBlank();
        assertThat(InetAddress.getByName(ip).isLoopbackAddress()).isFalse();
    }

    @Test
    void internalInterfacesAreIgnored() {
        assertThat(WorkerNodeAddress.isInternalInterface("docker0")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("docker_gwbridge")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("br-8f3a2b1c9d")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("bridge0")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("veth12ab34")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("virbr0")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("cni0")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("flannel.1")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("cali1234567890")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("lo")).isTrue();
        assertThat(WorkerNodeAddress.isInternalInterface("lo0")).isTrue();
    }

    @Test
    void physicalInterfacesAreNotIgnored() {
        assertThat(WorkerNodeAddress.isInternalInterface("eth0")).isFalse();
        assertThat(WorkerNodeAddress.isInternalInterface("ens33")).isFalse();
        assertThat(WorkerNodeAddress.isInternalInterface("en0")).isFalse();
        assertThat(WorkerNodeAddress.isInternalInterface("bond0")).isFalse();
        assertThat(WorkerNodeAddress.isInternalInterface(null)).isTrue();
    }

    @Test
    void loopbackAndLinkLocalAddressesAreNotUsable() throws Exception {
        assertThat(WorkerNodeAddress.isUsableAddress(InetAddress.getByName("127.0.0.1"))).isFalse();
        assertThat(WorkerNodeAddress.isUsableAddress(InetAddress.getByName("169.254.10.20"))).isFalse();
        assertThat(WorkerNodeAddress.isUsableAddress(InetAddress.getByName("10.20.30.40"))).isTrue();
        assertThat(WorkerNodeAddress.isUsableAddress(InetAddress.getByName("192.168.1.10"))).isTrue();
    }

    @Test
    void ipv6AddressIsBracketedWhenBuildingHost() throws Exception {
        InetAddress ipv6 = InetAddress.getByName("fd00::1");
        assertThat(WorkerNodeAddress.formatAddress(ipv6)).isEqualTo("[" + ipv6.getHostAddress() + "]");
        assertThat(WorkerNodeAddress.formatAddress(InetAddress.getByName("10.20.30.40"))).isEqualTo("10.20.30.40");
    }
}
