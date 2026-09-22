package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.DebugLimitReachedException;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * GenerateSequenceNumber组件 - 用于生成序列数
 * 支持指定开始和结束数据，按顺序生成flowfile并传递给下游
 * 开始和结束值支持参数替换
 */
@ComponentRegister(
    value = "GenerateSequenceNumber",
    name = "生成序列数",
    group = "调试组件",
    desc = "按指定起止值生成序列数据，支持从数据库读取，用于测试和调试。",
    order = 30
)
public class GenerateSequenceNumber extends FlowComponent {

    // 序列开始值
    private String startValue = "1";
    
    // 序列结束值
    private String countValue = "1";

    // 数据格式类型
    private String dataFormat = "TEXT";
    
    // 序列值长度，用于控制输出格式（前面补充0）
    private int length = 0;
    
    // 数据库模式：SEQUENCE（序列数）或 DATABASE（数据库读取）
    private String mode = "SEQUENCE";
    
    // 数据库连接配置（DATABASE模式使用）
    private DatasourceInfo datasource;
    
    // SQL查询语句（DATABASE模式使用）
    private String sql;
    
    // 表名（DATABASE模式使用，可替代sql）
    private String table;

    public GenerateSequenceNumber() {
        setType(ComponentType.SOURCE);  // 设置为Source类型
    }

    @Override
    public void execute(FlowFile flowFile) {
        if(flowFile.getAttribute("_end") != null) {
            return;
        }

        try {
            if ("DATABASE".equalsIgnoreCase(mode)) {
                executeDatabaseMode(flowFile);
            } else {
                executeSingleOutput(flowFile);
            }
        } catch (DebugLimitReachedException e) {
            // 调试采样上限，交由框架正常结束
            throw e;
        } catch (Exception e) {
            logError("生成序列数时发生错误: " + e.getMessage());
            throw new RuntimeException("生成序列数失败", e);
        }
    }

    /**
     * 单次输出模式
     */
    private void executeSingleOutput(FlowFile flowFile) {
        // 解析开始和结束值（支持参数替换）
        int start = parseNumberValue(startValue, flowFile);
        int count = parseNumberValue(countValue, flowFile);
        
        // 生成序列数并输出
        generateAndOutputSequence(start, count);
    }

    /**
     * 数据库模式：从数据库读取记录并生成FlowFile
     */
    private void executeDatabaseMode(FlowFile flowFile) {
        if (datasource == null) {
            throw new IllegalArgumentException("数据库模式需要配置datasource参数");
        }
        
        String querySql = buildQuerySql();
        
        try (Connection conn = DBUtils.getConnection(datasource.getUrl(), datasource.getUsername(), datasource.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySql)) {
            
            int recordCount = 0;
            
            // 获取结果集元数据
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                recordCount++;
                
                // 为每条记录生成一个FlowFile
                FlowFile flowFileRecord = generateFlowFileFromDatabaseRecord(rs, metaData, columnCount, recordCount);
                
                // 输出FlowFile
                writeRecords(flowFileRecord);
            }
            
            logInfo("数据库模式处理完成，共生成 " + recordCount + " 条记录");
            
        } catch (Exception e) {
            logError("数据库模式执行失败: " + e.getMessage());
            throw new RuntimeException("数据库模式执行失败", e);
        }
    }

    /**
     * 生成序列数并输出FlowFile
     */
    private void generateAndOutputSequence(int start, int count) {

        for (int i = 0; i < count; i++) {
            int currentValue = start + i;
            
            // 生成FlowFile
            FlowFile flowFile = generateFlowFileFromSequence(currentValue);
            
            // 输出FlowFile
            writeRecords(flowFile);

            logInfo("生成序列数: " + currentValue);
        }
    }

    /**
     * 根据序列数生成FlowFile
     */
    private FlowFile generateFlowFileFromSequence(int sequenceValue) {
        FlowFile flowFile = new FlowFile();
        String formattedValue = formatSequenceValue(sequenceValue);
        
        // 设置通用属性
        flowFile.setAttribute("_source", "GenerateSequenceNumber");
        flowFile.setAttribute("_timestamp", System.currentTimeMillis());
        flowFile.setAttribute("_index", formattedValue);
        flowFile.setAttribute("_mode", mode);

        // 根据数据格式生成相应的FlowFile内容
        switch (dataFormat.toUpperCase()) {
            case "TEXT":
                generateTextFlowFile(flowFile, sequenceValue);
                break;
            case "JSON_ARRAY":
                generateJsonArrayFlowFile(flowFile, sequenceValue);
                break;
            default:
                throw new IllegalArgumentException("不支持的数据格式: " + dataFormat);
        }
        
        return flowFile;
    }

    /**
     * 根据数据库记录生成FlowFile
     */
    private FlowFile generateFlowFileFromDatabaseRecord(ResultSet rs, java.sql.ResultSetMetaData metaData, int columnCount, int recordIndex) throws Exception {
        FlowFile flowFile = new FlowFile();
        
        // 设置通用属性
        flowFile.setAttribute("_source", "GenerateSequenceNumber");
        flowFile.setAttribute("_timestamp", System.currentTimeMillis());
        flowFile.setAttribute("_index", recordIndex);
        flowFile.setAttribute("_mode", mode);
        flowFile.setAttribute("_recordCount", recordIndex);
        
        // 将数据库字段名和值作为FlowFile属性
        Map<String, Object> recordData = new HashMap<>();
        
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            Object columnValue = rs.getObject(i);
            
            // 设置字段值作为属性
            if (columnValue != null) {
                flowFile.setAttribute(columnName, columnValue.toString());
                recordData.put(columnName, columnValue);
            } else {
                flowFile.setAttribute(columnName, null);
                recordData.put(columnName, null);
            }
        }
        
        // 根据数据格式生成相应的FlowFile内容
        switch (dataFormat.toUpperCase()) {
            case "TEXT":
                // 文本格式：将记录数据转换为JSON字符串
                flowFile.setTextData(com.alibaba.fastjson2.JSON.toJSONString(recordData));
                break;
            case "JSON_ARRAY":
                // JSON数组格式：将记录数据作为JSON对象放入数组
                JSONArray jsonArray = new JSONArray();
                jsonArray.add(recordData);
                flowFile.setJsonArray(jsonArray);
                break;
            default:
                throw new IllegalArgumentException("不支持的数据格式: " + dataFormat);
        }
        
        return flowFile;
    }

    /**
     * 生成文本格式的FlowFile
     */
    private void generateTextFlowFile(FlowFile flowFile, int sequenceValue) {
        String formattedValue = formatSequenceValue(sequenceValue);
        flowFile.setTextData(formattedValue);
    }

    /**
     * 生成JSON数组格式的FlowFile
     */
    private void generateJsonArrayFlowFile(FlowFile flowFile, int sequenceValue) {
        JSONArray jsonArray = new JSONArray();
        jsonArray.add(formatSequenceValue(sequenceValue));
        flowFile.setJsonArray(jsonArray);
    }

    /**
     * 解析数值（支持参数替换）
     */
    private int parseNumberValue(String value, FlowFile flowFile) {
        try {
            // 先进行参数替换，支持序列数相关参数
            String replacedValue = ParameterUtil.replaceParameters(value, flowFile);
            return Integer.parseInt(replacedValue);
        } catch (NumberFormatException e) {
            logError("数值解析失败: " + value + "，错误: " + e.getMessage());
            throw new IllegalArgumentException("无效的数值格式: " + value);
        }
    }

    /**
     * 构建查询SQL
     */
    private String buildQuerySql() {
        if (sql != null && !sql.trim().isEmpty()) {
            return sql;
        }
        
        if (table != null && !table.trim().isEmpty()) {
            return "SELECT * FROM " + table;
        }
        
        throw new IllegalArgumentException("数据库模式需要配置sql或table参数");
    }

    /**
     * 格式化序列值，根据长度参数在前面补充0
     */
    private String formatSequenceValue(int sequenceValue) {
        if (length <= 0) {
            return String.valueOf(sequenceValue);
        }
        
        // 使用String.format进行格式化，前面补充0
        return String.format("%0" + length + "d", sequenceValue);
    }

    /**
     * 计算序列数数量
     */
    private int calculateSequenceCount(int start, int end) {
        
        int count = (end - start) + 1;
        if (count <= 0) {
            throw new IllegalArgumentException("序列数数量必须大于0");
        }
        
        return count;
    }

    // Getter和Setter方法，用于参数注入

    public String getStartValue() {
        return startValue;
    }

    public void setStartValue(String startValue) {
        this.startValue = startValue;
    }

    public String getCountValue() {
        return countValue;
    }

    public void setCountValue(String countValue) {
        this.countValue = countValue;
    }

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public DatasourceInfo getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }
}