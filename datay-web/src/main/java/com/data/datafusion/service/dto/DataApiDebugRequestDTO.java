package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 数据服务调试请求体。
 *
 * <p>{@code dataApi} 为当前编辑中的服务配置（允许未保存），{@code params} 为本次调试传入的调用参数。</p>
 */
public class DataApiDebugRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private DataApiDTO dataApi;

    private Map<String, Object> params;

    public DataApiDTO getDataApi() {
        return dataApi;
    }

    public void setDataApi(DataApiDTO dataApi) {
        this.dataApi = dataApi;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
