package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.FileStorageConfigService;
import com.data.file.FileStorageStrategy;
import com.data.file.LocalFileStorageStrategy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link DelegatingFileStorageStrategy} 的单元测试。
 */
class DelegatingFileStorageStrategyTest {

    @TempDir
    Path tempDir;

    private DelegatingFileStorageStrategy strategy;

    @BeforeEach
    void setUp() {
        FileStorageConfigService configService = mock(FileStorageConfigService.class);
        when(configService.getOrInitializeStrategy()).thenReturn(new LocalFileStorageStrategy(tempDir.toString()));
        strategy = new DelegatingFileStorageStrategy(configService);
        TenantContext.setTenantCode("system");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listReturnsTenantRelativePathsWithForwardSlashes() {
        strategy.mkdir("test");
        upload("test/a.txt", "hello");

        List<FileStorageStrategy.FileInfo> root = strategy.list("");
        assertThat(root).extracting(FileStorageStrategy.FileInfo::path).containsExactly("test");

        List<FileStorageStrategy.FileInfo> files = strategy.list("test");
        assertThat(files).extracting(FileStorageStrategy.FileInfo::path).containsExactly("test/a.txt");
        assertThat(Files.exists(tempDir.resolve("tenants/system/test/a.txt"))).isTrue();
    }

    @Test
    void normalizesLegacyWindowsPaths() {
        upload(".\\data\\files\\tenants\\system\\test\\a.txt", "hello");

        assertThat(tempDir.resolve("tenants/system/test/a.txt")).exists();
        List<FileStorageStrategy.FileInfo> root = strategy.list("");
        assertThat(root).extracting(FileStorageStrategy.FileInfo::path).containsExactly("test");
    }

    @Test
    void normalizesLegacyAbsoluteWindowsPaths() {
        upload("D:\\TMIC\\DataY\\data\\files\\tenants\\system\\test\\a.txt", "hello");

        assertThat(tempDir.resolve("tenants/system/test/a.txt")).exists();
    }

    private void upload(String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        strategy.upload(path, new ByteArrayInputStream(bytes), bytes.length, "text/plain");
    }
}
