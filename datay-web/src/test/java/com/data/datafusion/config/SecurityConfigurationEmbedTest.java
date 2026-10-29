package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SecurityConfiguration#withFrameAncestors(String, String)} 的单元测试。
 */
class SecurityConfigurationEmbedTest {

    @Test
    void shouldAppendFrameAncestorsToExistingPolicy() {
        String result = SecurityConfiguration.withFrameAncestors("default-src 'self'", "*");

        assertThat(result).isEqualTo("default-src 'self'; frame-ancestors *");
    }

    @Test
    void shouldSupportOriginWhitelist() {
        String result = SecurityConfiguration.withFrameAncestors(
            "default-src 'self'",
            "https://a.example.com https://b.example.com"
        );

        assertThat(result).isEqualTo(
            "default-src 'self'; frame-ancestors https://a.example.com https://b.example.com"
        );
    }

    @Test
    void shouldReturnOnlyDirectiveWhenPolicyIsMissing() {
        assertThat(SecurityConfiguration.withFrameAncestors(null, "*")).isEqualTo("frame-ancestors *");
        assertThat(SecurityConfiguration.withFrameAncestors("  ", null)).isEqualTo("frame-ancestors *");
    }

    @Test
    void shouldFallbackToWildcardWhenOriginsAreBlank() {
        assertThat(SecurityConfiguration.withFrameAncestors("default-src 'self'", "  ")).isEqualTo(
            "default-src 'self'; frame-ancestors *"
        );
    }

    @Test
    void shouldNotDuplicateExistingFrameAncestorsDirective() {
        String policy = "default-src 'self'; frame-ancestors 'none'";

        assertThat(SecurityConfiguration.withFrameAncestors(policy, "*")).isEqualTo(policy);
    }
}