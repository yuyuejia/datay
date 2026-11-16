package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.List;

/**
 * 任务上线时的依赖配置请求体。
 *
 * <p>{@code dependencies} 为 {@code null} 表示不修改既有依赖；空列表表示清空依赖。
 */
public class TaskDependRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<JobDependDTO> dependencies;

    public List<JobDependDTO> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<JobDependDTO> dependencies) {
        this.dependencies = dependencies;
    }
}
