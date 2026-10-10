package com.data.datafusion.config;

import com.data.metadata.util.DriverDownloader;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 将 {@code datay.driver.repos} 配置注入到核心模块的 {@link DriverDownloader}。
 *
 * <p>核心模块为静态工具类，无法直接使用 Spring 配置，故在此统一桥接。
 */
@Configuration
public class DriverDownloadConfiguration {

    public DriverDownloadConfiguration(@Value("${datay.driver.repos:}") String repos) {
        if (repos == null || repos.isBlank()) {
            return;
        }
        List<String> urls = Arrays
            .stream(repos.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        if (!urls.isEmpty()) {
            DriverDownloader.setRepoUrls(urls);
        }
    }
}
