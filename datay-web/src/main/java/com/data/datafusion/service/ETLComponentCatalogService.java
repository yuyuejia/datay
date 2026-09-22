package com.data.datafusion.service;

import com.data.datafusion.service.dto.ETLComponentDTO;
import com.data.datafusion.service.etl.ETLNodeTranslator;
import com.data.job.ComponentDescriptor;
import com.data.job.ComponentFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * ETL 组件目录服务。
 * <p>
 * 汇总 DataY Core 自动发现的组件（{@link ComponentFactory}）与设计器侧翻译器声明的组件
 * （{@link ETLNodeTranslator#descriptor()}），向前端组件面板提供统一的组件目录，
 * 无需再将组件注册到数据库。
 */
@Service
public class ETLComponentCatalogService {

    private final List<ETLNodeTranslator> translators;

    public ETLComponentCatalogService(List<ETLNodeTranslator> translators) {
        this.translators = translators;
    }

    /**
     * 获取全部可用的 ETL 组件目录。
     *
     * @return 组件目录
     */
    public List<ETLComponentDTO> listCatalog() {
        Map<String, ComponentDescriptor> merged = new LinkedHashMap<>();
        for (ComponentDescriptor descriptor : ComponentFactory.listDescriptors()) {
            merged.put(descriptor.getCode(), descriptor);
        }
        // 设计器侧独有组件（如「模型写入」）由翻译器声明，同名时以翻译器声明为准
        if (translators != null) {
            for (ETLNodeTranslator translator : translators) {
                ComponentDescriptor descriptor = translator.descriptor();
                if (descriptor != null && descriptor.getCode() != null) {
                    merged.put(descriptor.getCode(), descriptor);
                }
            }
        }

        List<ETLComponentDTO> catalog = new ArrayList<>(merged.size());
        for (ComponentDescriptor descriptor : merged.values()) {
            catalog.add(toDto(descriptor));
        }
        return catalog;
    }

    private ETLComponentDTO toDto(ComponentDescriptor descriptor) {
        ETLComponentDTO dto = new ETLComponentDTO();
        dto.setName(descriptor.getName());
        dto.setCode(descriptor.getCode());
        dto.setDesc(descriptor.getDesc());
        dto.setGroup(descriptor.getGroup());
        dto.setType("0");
        dto.setConfig("");
        dto.setStatus("0");
        return dto;
    }
}
