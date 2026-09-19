package com.data.datafusion.config;

import com.data.datafusion.service.FileStorageConfigService;
import com.data.file.FileStorageStrategy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileStorageConfiguration {

    @Bean
    public FileStorageStrategy fileStorageStrategy(FileStorageConfigService configService) {
        return configService.getOrInitializeStrategy();
    }
}