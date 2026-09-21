package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.tool.DatabaseSchemaQueryTool;
import com.data.datafusion.ai.tool.SqlValidateTool;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SQL 任务助手。
 *
 * <p>面向 SQL 任务编辑页，生成可写入调度任务的 SQL 脚本，允许 DDL/DML 与多条语句，
 * 但仅通过解析校验确认语法，不执行任何写操作。
 */
@Component
@Order(10)
public class SqlTaskAssistant implements AiAssistant {

    public static final String ID = "sql-task";

    private static final List<String> SAMPLES = List.of(
        "创建一张用户表，包含 id、姓名、邮箱、创建时间字段",
        "把 orders 表中金额大于 1000 的订单同步到 vip_orders 表",
        "给 users 表新增一个 last_login_time 字段",
        "清空临时表 tmp_log 并重新初始化统计数据"
    );

    private static final String FINALIZE_INSTRUCTION = """
        你已获得足够信息，请立即给出最终 SQL。不要再请求任何工具调用。
        输出格式要求：
        1. 用 ```sql 代码块给出**完整可执行**的 SQL（DDL/DML/SELECT 均可，如有多条请按执行顺序全部列出）；
        2. 代码块之后用简体中文简要说明每条语句的用途。
        """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "AI SQL 任务助手";
    }

    @Override
    public String description() {
        return "用一句话描述你的 SQL 任务（建表、写入、查询等），我来生成脚本";
    }

    @Override
    public boolean allowWrites() {
        return true;
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    /**
     * 任务助手可使用：元数据查询与 SQL 校验。
     * 不包含预览工具，避免对可能含写操作的脚本做试跑。
     */
    @Override
    public Set<String> toolNames() {
        return Set.of(DatabaseSchemaQueryTool.NAME, SqlValidateTool.NAME);
    }

    @Override
    public String finalizeInstruction() {
        return FINALIZE_INSTRUCTION;
    }

    @Override
    public String buildSystemPrompt(AiAssistantContext context) {
        DataSourceDTO dataSource = context.getDataSource();
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的「SQL 任务」编写助手，职责是把用户的自然语言需求翻译成可写入 SQL 任务的 SQL 脚本。\n");
        sb.append("SQL 任务常用于数据加工与调度，语句可能是查询，也可能是建表、写入、更新等 DDL/DML。\n\n");
        sb.append("工作方式：\n");
        sb.append("1. 先判断信息是否充分。若不清楚有哪些表、哪些字段，主动调用工具去查，不要凭经验猜测表名与字段名。\n");
        sb.append("2. 典型的探索路径：list_database_objects(schemas) → list_database_objects(tables) → list_database_objects(columns)。\n");
        sb.append("3. 写出 SQL 后，调用 validate_sql 自检语法；若有错误，依据报错修正后重新校验。\n");
        sb.append("4. 确认无误后直接给出最终答案，不必再有冗长铺垫。\n\n");
        sb.append("硬性约束：\n");
        sb.append("- 允许生成 DDL（CREATE/ALTER/DROP/TRUNCATE 等）与 DML（INSERT/UPDATE/DELETE/MERGE 等），也允许 SELECT。\n");
        sb.append("- 不要调用试跑工具执行写操作；validate_sql 仅做解析校验，不会真正执行。\n");
        sb.append("- 严禁编造不存在的表名或字段名，所有标识符必须来自工具查询的真实元数据。\n");
        sb.append("- SQL 必须适配当前数据源的方言。\n");
        sb.append("- 输出用简体中文说明，SQL 放在 ```sql 代码块中。\n\n");
        if (dataSource != null) {
            sb.append("当前数据源信息：\n");
            sb.append("- 名称：").append(nullSafe(dataSource.getName())).append('\n');
            sb.append("- 类型：").append(nullSafe(dataSource.getType())).append('\n');
            if (dataSource.getSchemaName() != null && !dataSource.getSchemaName().isBlank()) {
                sb.append("- 默认 schema：").append(dataSource.getSchemaName()).append('\n');
            }
            sb.append("- 数据源已在会话上下文中绑定，调用工具时无需传入数据源标识。\n");
        } else {
            sb.append("本次会话未绑定具体数据源，若用户需要真实表结构，请提示其在「SQL 任务编辑页」选择数据源后再试。\n");
        }
        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
