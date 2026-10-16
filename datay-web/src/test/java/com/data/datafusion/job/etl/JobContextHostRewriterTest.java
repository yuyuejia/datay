package com.data.datafusion.job.etl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link JobContextHostRewriter} 的单元测试。
 */
class JobContextHostRewriterTest {

    private static final String ALIAS = "host.docker.internal";

    @Test
    void rewritesJdbcLoopbackUrl() {
        String json =
            "{\"units\":[{\".id\":\"a\",\".name\":\"StreamJdbcInput\"," +
            "\"datasource\":{\"url\":\"jdbc:mysql://127.0.0.1:3306/source\",\"username\":\"root\"}}]}";
        String result = JobContextHostRewriter.rewriteLoopback(json, ALIAS);
        assertThat(result).contains("jdbc:mysql://host.docker.internal:3306/source");
    }

    @Test
    void rewritesLoopbackInSourceIdAndHostname() {
        String json =
            "{\"units\":[{\".id\":\"a\",\"sourceId\":{\"hostname\":\"localhost\"," +
            "\"url\":\"jdbc:postgresql://localhost:5432/db\"}}]}";
        String result = JobContextHostRewriter.rewriteLoopback(json, ALIAS);
        assertThat(result).contains("\"hostname\":\"host.docker.internal\"");
        assertThat(result).contains("jdbc:postgresql://host.docker.internal:5432/db");
    }

    @Test
    void rewritesHttpServiceUrl() {
        String json = "{\"units\":[{\".name\":\"DecisionComponent\",\"baseUrl\":\"http://localhost:11434/v1\"}]}";
        String result = JobContextHostRewriter.rewriteLoopback(json, ALIAS);
        assertThat(result).contains("http://host.docker.internal:11434/v1");
    }

    @Test
    void keepsRemoteHostsUnchanged() {
        String json = "{\"datasource\":{\"url\":\"jdbc:mysql://10.0.0.5:3306/db\"}}";
        String result = JobContextHostRewriter.rewriteLoopback(json, ALIAS);
        assertThat(result).contains("10.0.0.5:3306").doesNotContain(ALIAS);
    }

    @Test
    void invalidJsonReturnedAsIs() {
        assertThat(JobContextHostRewriter.rewriteLoopback("{ not-json", ALIAS)).isEqualTo("{ not-json");
    }

    @Test
    void blankInputReturnedAsIs() {
        assertThat(JobContextHostRewriter.rewriteLoopback("", ALIAS)).isEmpty();
        assertThat(JobContextHostRewriter.rewriteLoopback(null, ALIAS)).isNull();
    }
}
