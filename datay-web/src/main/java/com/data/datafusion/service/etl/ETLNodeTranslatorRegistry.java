package com.data.datafusion.service.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ETL 节点翻译器注册表。
 * <p>自动收集容器中所有 {@link ETLNodeTranslator} 实现，按 {@link ETLNodeTranslator#supportedType()} 建立索引。
 * 新增组件翻译只需新增一个实现类，注册表会通过 Spring 依赖注入自动装配。
 */
@Component
public class ETLNodeTranslatorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ETLNodeTranslatorRegistry.class);

    private final Map<String, ETLNodeTranslator> translators;

    public ETLNodeTranslatorRegistry(List<ETLNodeTranslator> translatorList) {
        Map<String, ETLNodeTranslator> map = new LinkedHashMap<>();
        if (translatorList != null) {
            for (ETLNodeTranslator translator : translatorList) {
                String type = translator.supportedType();
                if (type == null || type.trim().isEmpty()) {
                    LOG.warn("忽略未声明 supportedType 的 ETLNodeTranslator: {}", translator.getClass().getName());
                    continue;
                }
                ETLNodeTranslator exists = map.put(type, translator);
                if (exists != null) {
                    LOG.warn("组件 {} 存在多个翻译器，后注册的 {} 将覆盖 {}", type, translator.getClass().getName(), exists.getClass().getName());
                }
            }
        }
        this.translators = Collections.unmodifiableMap(map);
        LOG.info("已注册 ETL 节点翻译器: {}", this.translators.keySet());
    }

    /**
     * 判断是否存在指定组件类型的翻译器。
     *
     * @param type 组件类型
     * @return 是否存在
     */
    public boolean supports(String type) {
        return type != null && translators.containsKey(type);
    }

    /**
     * 获取指定组件类型的翻译器。
     *
     * @param type 组件类型
     * @return 翻译器，不存在时返回 {@code null}
     */
    public ETLNodeTranslator get(String type) {
        return type == null ? null : translators.get(type);
    }
}
