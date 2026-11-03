package com.data.datafusion.ai.assistant;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * ETL 组件帮助文档注册表。
 *
 * <p>文档源文件维护在仓库根目录 {@code docs/help/components/*.md}，构建时会被复制到
 * classpath 的 {@code docs/help/components} 下。运行期按以下顺序加载：
 * <ol>
 *     <li>classpath: {@code docs/help/components/*.md}</li>
 *     <li>外部目录：{@code datay.ai.component-docs-path}（可选，用于不重新打包更新文档）</li>
 *     <li>本地开发兜底：{@code ../docs/help/components}、{@code docs/help/components}</li>
 * </ol>
 *
 * <p>文档只作为只读元数据使用，不存在写操作。
 */
@Component
public class EtlComponentDocRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(EtlComponentDocRegistry.class);

    private static final String CLASSPATH_PATTERN = "classpath*:docs/help/components/*.md";

    private final MarkdownDocParser parser;

    @Value("${datay.ai.component-docs-path:}")
    private String configuredDocsPath;

    private volatile Map<String, EtlComponentDoc> docs = Map.of();

    private volatile Map<String, EtlComponentDoc> lowerCaseIndex = Map.of();

    public EtlComponentDocRegistry(MarkdownDocParser parser) {
        this.parser = parser;
    }

    @PostConstruct
    public void load() {
        Map<String, EtlComponentDoc> loaded = new LinkedHashMap<>();
        loadFromClasspath(loaded);
        if (configuredDocsPath != null && !configuredDocsPath.isBlank()) {
            loadFromDirectory(loaded, configuredDocsPath.trim(), true);
        }
        loadFromDirectory(loaded, "../docs/help/components", false);
        loadFromDirectory(loaded, "docs/help/components", false);

        Map<String, EtlComponentDoc> index = new LinkedHashMap<>();
        for (EtlComponentDoc doc : loaded.values()) {
            if (doc.type() != null && !doc.type().isBlank()) {
                index.put(doc.type().trim().toLowerCase(Locale.ROOT), doc);
            }
        }
        this.docs = Collections.unmodifiableMap(loaded);
        this.lowerCaseIndex = Collections.unmodifiableMap(index);
        LOG.info("Loaded {} ETL component docs, types={}", docs.size(), docs.keySet());
    }

    /**
     * 按组件编码查找帮助文档，大小写不敏感。
     */
    public Optional<EtlComponentDoc> find(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lowerCaseIndex.get(type.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 是否包含指定组件的帮助文档。
     */
    public boolean contains(String type) {
        return find(type).isPresent();
    }

    /**
     * 全部已加载文档。
     */
    public Collection<EtlComponentDoc> all() {
        return docs.values();
    }

    /**
     * 全部已加载文档的组件编码。
     */
    public List<String> types() {
        return List.copyOf(docs.keySet());
    }

    private void loadFromClasspath(Map<String, EtlComponentDoc> target) {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_PATTERN);
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    String markdown = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    register(target, resource.getFilename(), markdown, false);
                }
            }
        } catch (IOException e) {
            LOG.warn("加载 classpath ETL 组件帮助文档失败: {}", e.getMessage());
        }
    }

    private void loadFromDirectory(Map<String, EtlComponentDoc> target, String directory, boolean override) {
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path path = Paths.get(directory);
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> files = Files.list(path)) {
            files
                .filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                .sorted()
                .forEach(file -> {
                    try {
                        String markdown = Files.readString(file, StandardCharsets.UTF_8);
                        register(target, file.getFileName().toString(), markdown, override);
                    } catch (IOException e) {
                        LOG.warn("读取 ETL 组件帮助文档失败: {} - {}", file, e.getMessage());
                    }
                });
        } catch (IOException e) {
            LOG.warn("扫描 ETL 组件帮助文档目录失败: {} - {}", directory, e.getMessage());
        }
    }

    private void register(Map<String, EtlComponentDoc> target, String filename, String markdown, boolean override) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
            return;
        }
        String type = filename.substring(0, filename.length() - 3);
        if ("README".equalsIgnoreCase(type) || type.isBlank()) {
            return;
        }
        try {
            EtlComponentDoc doc = parser.parse(type, markdown);
            if (override) {
                target.put(type, doc);
            } else {
                target.putIfAbsent(type, doc);
            }
        } catch (Exception e) {
            LOG.warn("解析 ETL 组件帮助文档失败: {} - {}", filename, e.getMessage());
        }
    }
}
