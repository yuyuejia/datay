package com.data.datafusion.service.datamap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据地图配置。
 *
 * <p>数据地图索引是平台对象与采集元数据的派生数据，默认存放在独立 DuckDB 文件中，
 * 可通过 {@link #provider} 扩展为其他检索/图存储实现。</p>
 */
@Component
@ConfigurationProperties(prefix = "datay.data-map")
public class DataMapProperties {

    /** 是否启用数据地图。 */
    private boolean enabled = true;

    /** 索引文件路径，默认与指标 RAG 索引隔离。 */
    private String dbFile = "./data/data-map.duckdb";

    /** 存储提供方，目前支持 duckdb。 */
    private String provider = "duckdb";

    /** 单次检索默认返回条数。 */
    private int topK = 50;

    /** 全量图最大节点数，超过后截断并提示。 */
    private int maxFullNodes = 8000;

    /** 全量图最大边数，超过后截断并提示。 */
    private int maxFullEdges = 30000;

    /** 聚焦图最大节点数。 */
    private int maxSubgraphNodes = 500;

    /** 图查询最大深度。 */
    private int maxDepth = 3;

    /** 索引为空或过期时，是否在首次访问时自动后台构建。 */
    private boolean autoBuild = true;

    /** 元数据采集成功后是否将索引标记为过期。 */
    private boolean rebuildOnMetadataCollect = true;

    /** 全量图默认是否包含字段节点。 */
    private boolean includeColumns = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDbFile() {
        return dbFile;
    }

    public void setDbFile(String dbFile) {
        this.dbFile = dbFile;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxFullNodes() {
        return maxFullNodes;
    }

    public void setMaxFullNodes(int maxFullNodes) {
        this.maxFullNodes = maxFullNodes;
    }

    public int getMaxFullEdges() {
        return maxFullEdges;
    }

    public void setMaxFullEdges(int maxFullEdges) {
        this.maxFullEdges = maxFullEdges;
    }

    public int getMaxSubgraphNodes() {
        return maxSubgraphNodes;
    }

    public void setMaxSubgraphNodes(int maxSubgraphNodes) {
        this.maxSubgraphNodes = maxSubgraphNodes;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public boolean isAutoBuild() {
        return autoBuild;
    }

    public void setAutoBuild(boolean autoBuild) {
        this.autoBuild = autoBuild;
    }

    public boolean isRebuildOnMetadataCollect() {
        return rebuildOnMetadataCollect;
    }

    public void setRebuildOnMetadataCollect(boolean rebuildOnMetadataCollect) {
        this.rebuildOnMetadataCollect = rebuildOnMetadataCollect;
    }

    public boolean isIncludeColumns() {
        return includeColumns;
    }

    public void setIncludeColumns(boolean includeColumns) {
        this.includeColumns = includeColumns;
    }
}
