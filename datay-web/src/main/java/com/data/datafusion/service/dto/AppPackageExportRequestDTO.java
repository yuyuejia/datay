package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产包导出请求：从当前租户中挑选要打包的资产。
 *
 * <p>纯 RPC 载荷（不落库），字段直接公开以便与 JSON 一一对应。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageExportRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 资产包名称。 */
    public String name;

    /** 资产包编码，留空时按名称生成。 */
    public String code;

    public String description;

    /** 业务场景分类。 */
    public String category;

    /** 版本号，默认 1.0.0。 */
    public String version;

    /** 选中的数据源 ID。 */
    public List<String> dataSourceIds = new ArrayList<>();

    /** 选中的数据模型 ID。 */
    public List<String> modelIds = new ArrayList<>();

    /** 选中的指标 ID。 */
    public List<String> metricIds = new ArrayList<>();

    /** 选中的 ETL 任务 ID。 */
    public List<String> etlTaskIds = new ArrayList<>();

    /** 选中的 SQL 任务（Job） ID。 */
    public List<String> sqlJobIds = new ArrayList<>();

    /** 选中的编排任务（Job） ID。 */
    public List<String> dagJobIds = new ArrayList<>();

    /** 是否自动补齐被引用的资产（指标引用的事实模型、模型引用的数据源、编排引用的子任务等），默认 true。 */
    public Boolean includeReferences = Boolean.TRUE;

    /** 是否把生成的资产包保存到市场，默认 true。 */
    public Boolean saveToMarket = Boolean.TRUE;

    /** 同名/同编码资产包已存在时是否覆盖，默认 false（生成新的编码）。 */
    public Boolean overwrite = Boolean.FALSE;
}
