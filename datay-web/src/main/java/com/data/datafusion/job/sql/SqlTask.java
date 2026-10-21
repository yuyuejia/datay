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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile Connection connection;

    private volatile Statement currentStatement;

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

        if (cancelled.get()) {
            throw new InterruptedException("SQL task was cancelled before execution");
        }

        this.log("SQL task [" + getJobInstance().getJobName() + "] is executing...");
        try {
            Connection dbConnection = DBUtils.getConnection(datasourceInfo);
            this.connection = dbConnection;
            if (cancelled.get()) {
                throw new InterruptedException("SQL task was cancelled before execution");
            }
            try (Connection ignored = dbConnection) {
                ScriptRunner scriptRunner = new ScriptRunner(dbConnection);
                scriptRunner.setStopOnError(true);
                scriptRunner.setStatementListener(statement -> this.currentStatement = statement);
                // 将 ScriptRunner 的执行日志输出到 TaskLogger 的日志文件中
                OutputStream taskLogStream = this.getTaskLogger().getLogWriter();
                PrintWriter writer = new PrintWriter(taskLogStream, true);
                scriptRunner.setLogWriter(writer);
                scriptRunner.setErrorLogWriter(writer);
                Reader reader = new StringReader(script);
                scriptRunner.runScript(reader);
                reader.close();
            }
        } catch (Exception e) {
            if (cancelled.get() || e instanceof InterruptedException) {
                throw new InterruptedException("SQL task was cancelled manually");
            }
            String hint = missingObjectHint(e.getMessage());
            String errorMsg = "SQL task [" + getJobInstance().getJobName() + "] execution failed: " + e.getMessage() + hint;
            this.log(errorMsg);
            throw new Exception("SQL 任务执行失败: " + e.getMessage() + hint, e);
        } finally {
            this.currentStatement = null;
            this.connection = null;
        }
        this.log("SQL task [" + getJobInstance().getJobName() + "] executed successfully");
        return "0";
    }

    @Override
    public boolean cancel() {
        cancelled.set(true);
        Statement statement = this.currentStatement;
        if (statement != null) {
            try {
                statement.cancel();
            } catch (SQLException e) {
                logger.warn("取消 SQL 语句执行失败: {}", e.getMessage());
            }
        }
        Connection currentConnection = this.connection;
        if (currentConnection != null) {
            try {
                currentConnection.abort(Runnable::run);
            } catch (Exception e) {
                logger.warn("中断 SQL 连接失败，尝试关闭连接: {}", e.getMessage());
                try {
                    currentConnection.close();
                } catch (SQLException closeError) {
                    logger.warn("关闭 SQL 连接失败: {}", closeError.getMessage());
                }
            }
        }
        return true;
    }

    /**
     * 对「表/视图不存在」这类错误补充可执行的排查提示。
     *
     * <p>典型场景：SQL 任务依赖的上游任务（ETL 写入的维度/事实表、前一个 SQL 任务产出的中间表）
     * 还没有执行，直接单跑该任务就只能看到一个很难理解的数据库原始报错。
     */
    static String missingObjectHint(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        boolean missing =
            lower.contains("does not exist") ||
            lower.contains("doesn't exist") ||
            lower.contains("unknown table") ||
            lower.contains("no such table") ||
            lower.contains("invalid object name") ||
            lower.contains("table or view not found");
        if (!missing) {
            return "";
        }
        return (
            "\n提示：SQL 中用到的表/视图不存在，通常是上游任务还没有执行。" +
            "请先运行该任务所属的「编排任务」（任务编排 → 执行一次），" +
            "或按编排中的依赖顺序先执行上游 ETL / SQL 任务，再重试本任务。"
        );
    }
}
