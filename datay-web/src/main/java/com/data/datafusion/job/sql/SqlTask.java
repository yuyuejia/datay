package com.data.datafusion.job.sql;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.AbstractTask;
import com.data.job.DatasourceInfo;
import com.data.metadata.util.DBUtils;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 任务执行器。
 *
 * <p>任务上下文（{@code jobContext}）由 {@link SqlTaskContextAssembler} 在任务下发前组装，
 * 约定包含：
 * <ul>
 *     <li>{@code sql}：待执行的 SQL 语句，支持以分号分隔的多条语句</li>
 *     <li>{@code dataSource}：数据源连接信息（url、driver、username、password、dbschema 等）</li>
 * </ul>
 * 本类不再查询数据源，直接使用 jobContext 中已组装好的连接信息。
 */
public class SqlTask extends AbstractTask {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public SqlTask(JobInstance taskInstance) {
        super(taskInstance);
    }

    @Override
    public String doExecute() throws Exception {
        JSONObject context = JSONObject.parseObject(getJobInstance().getJobContext());
        if (context == null) {
            throw new Exception("SQL 任务未配置执行内容");
        }
        String sql = context.getString("sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new Exception("SQL 任务未配置 SQL 语句");
        }
        JSONObject connectionConfig = context.getJSONObject("dataSource");
        if (connectionConfig == null) {
            throw new Exception("SQL 任务未组装数据源信息");
        }

        DatasourceInfo datasourceInfo = new DatasourceInfo();
        datasourceInfo.setType(connectionConfig.getString("type"));
        datasourceInfo.setUrl(connectionConfig.getString("url"));
        datasourceInfo.setHostname(connectionConfig.getString("hostname"));
        datasourceInfo.setPort(connectionConfig.getString("port"));
        datasourceInfo.setUsername(connectionConfig.getString("username"));
        datasourceInfo.setPassword(connectionConfig.getString("password"));
        datasourceInfo.setDriver(connectionConfig.getString("driver"));
        datasourceInfo.setDbschema(connectionConfig.getString("dbschema"));

        String script = sql.trim();
        if (!script.endsWith(";")) {
            script = script + ";";
        }

        this.log("SQL task [" + getJobInstance().getJobName() + "] is executing...");
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            ScriptRunner scriptRunner = new ScriptRunner(connection);
            scriptRunner.setStopOnError(true);
            // 将 ScriptRunner 的执行日志输出到 TaskLogger 的日志文件中
            OutputStream taskLogStream = this.getTaskLogger().getLogWriter();
            PrintWriter writer = new PrintWriter(taskLogStream, true);
            scriptRunner.setLogWriter(writer);
            scriptRunner.setErrorLogWriter(writer);
            Reader reader = new StringReader(script);
            scriptRunner.runScript(reader);
            reader.close();
        } catch (Exception e) {
            String errorMsg = "SQL task [" + getJobInstance().getJobName() + "] execution failed: " + e.getMessage();
            this.log(errorMsg);
            throw new Exception("SQL 任务执行失败: " + e.getMessage(), e);
        }
        this.log("SQL task [" + getJobInstance().getJobName() + "] executed successfully");
        return "0";
    }
}