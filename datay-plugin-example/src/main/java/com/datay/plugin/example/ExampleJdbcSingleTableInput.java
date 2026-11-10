package com.datay.plugin.example;

import com.data.job.ComponentField;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.job.component.StreamJdbcInput;

/**
 * 自定义 JDBC 单表输入组件示例。
 * <p>
 * 复用平台 {@link StreamJdbcInput} 的流式读取能力，通过自定义字段 key 演示：
 * <ul>
 *     <li>自定义 {@code DATASOURCE} 字段的数据源解析；</li>
 *     <li>数据源、Schema、表选择器之间的联动；</li>
 *     <li>自定义输入组件与 {@code StreamJdbcOutput} 的流式对接。</li>
 * </ul>
 */
@ComponentRegister(
    value = "ExampleJdbcSingleTableInput",
    name = "示例 JDBC 单表输入",
    group = "示例插件",
    desc = "自定义 JDBC 单表输入示例，读取指定数据源中的单表数据并交给下游。",
    order = 20
)
public class ExampleJdbcSingleTableInput extends StreamJdbcInput {

    @ComponentField(
        key = "sourceDatasource",
        label = "源数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "基础配置",
        order = 10,
        schemaField = "sourceSchema",
        help = "自定义 DATASOURCE 字段；平台会把它解析为完整数据源连接信息"
    )
    private DatasourceInfo sourceDatasource;

    @ComponentField(
        key = "sourceSchema",
        label = "Schema",
        type = ComponentFieldType.SCHEMA,
        group = "基础配置",
        order = 20,
        dataSourceField = "sourceDatasource",
        placeholder = "请选择 Schema"
    )
    private String sourceSchema;

    @ComponentField(
        key = "sourceTable",
        label = "源表",
        type = ComponentFieldType.TABLE,
        required = true,
        group = "基础配置",
        order = 30,
        dataSourceField = "sourceDatasource",
        schemaField = "sourceSchema",
        placeholder = "请选择源表"
    )
    private String sourceTable;

    @ComponentField(
        key = "sourceIncrColumn",
        label = "增量字段",
        type = ComponentFieldType.INPUT,
        group = "高级配置",
        order = 10,
        placeholder = "例如 update_time；留空表示全量同步",
        help = "按该字段做增量读取；留空时执行全表读取"
    )
    private String sourceIncrColumn;

    @ComponentField(
        key = "sourceWhere",
        label = "过滤条件",
        type = ComponentFieldType.TEXTAREA,
        group = "高级配置",
        order = 20,
        placeholder = "例如 status = 1",
        help = "可选的 WHERE 条件，不需要写 WHERE 关键字"
    )
    private String sourceWhere;

    @Override
    public void execute(FlowFile flowFile) {
        // 把自定义配置字段同步到 StreamJdbcInput 的读取参数
        this.datasource = sourceDatasource;
        this.schema = sourceSchema;
        this.table = sourceTable;
        this.incrColumn = sourceIncrColumn;
        this.where = sourceWhere;
        super.execute(flowFile);
    }

    public DatasourceInfo getSourceDatasource() {
        return sourceDatasource;
    }

    public void setSourceDatasource(DatasourceInfo sourceDatasource) {
        this.sourceDatasource = sourceDatasource;
    }

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSourceIncrColumn() {
        return sourceIncrColumn;
    }

    public void setSourceIncrColumn(String sourceIncrColumn) {
        this.sourceIncrColumn = sourceIncrColumn;
    }

    public String getSourceWhere() {
        return sourceWhere;
    }

    public void setSourceWhere(String sourceWhere) {
        this.sourceWhere = sourceWhere;
    }
}
