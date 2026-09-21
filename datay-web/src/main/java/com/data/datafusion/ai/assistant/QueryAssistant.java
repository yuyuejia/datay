package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.tool.DatabaseSchemaQueryTool;
import com.data.datafusion.ai.tool.SqlPreviewTool;
import com.data.datafusion.ai.tool.SqlValidateTool;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SQL 查询助手。
 *
 * <p>面向数据查询页，把自然语言数据需求翻译成单条只读 SELECT，
 * 强调先查元数据再写 SQL，并对结果做语法校验与轻量试跑。
 */
@Component
@Order(0)
public class QueryAssistant implements AiAssistant {

    public static final String ID = "query";

    private static final List<String> SAMPLES = List.of(
        "查询最近 7 天每天的订单总金额",
        "统计每个用户的下单次数，按次数倒序取前 20",
        "找出从未下过单的用户",
        "按月统计销售额环比增长"
    );

    private static final String FINALIZE_INSTRUCTION = """
        你已获得足够信息，请立即给出最终 SQL 答案。不要再请求任何工具调用。
        输出格式要求：
        1. 先用 ```sql 代码块给出**完整可执行**的 SQL（仅限单条 SELECT，禁止 DDL/DML）；
        2. 代码块之后用简体中文简要说明查询逻辑与涉及的表。
        """;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "AI SQL 查询助手";
    }

    @Override
    public String description() {
        return "用一句话描述你想查什么，我来生成只读查询 SQL";
    }

    @Override
    public List<String> samplePrompts() {
        return SAMPLES;
    }

    /**
     * 查询助手可使用：元数据查询、SQL 校验，以及小样本试跑。
     * 试跑仅执行 SELECT，故只读助手可用，写场景助手不含该工具。
     */
    @Override
    public Set<String> toolNames() {
        return Set.of(DatabaseSchemaQueryTool.NAME, SqlValidateTool.NAME, SqlPreviewTool.NAME);
    }

    @Override
    public String finalizeInstruction() {
        return FINALIZE_INSTRUCTION;
    }

    @Override
    public String buildSystemPrompt(AiAssistantContext context) {
        DataSourceDTO dataSource = context.getDataSource();
        StringBuilder sb = new StringBuilder();
        sb.append("你是 DataY 数据平台内置的 SQL 助手，职责是把用户的自然语言数据需求翻译成正确的 SQL。\n\n");
        sb.append("工作方式：\n");
        sb.append("1. 先判断信息是否充分。若不清楚有哪些表、哪些字段，主动调用工具去查，不要凭经验猜测表名与字段名。\n");
        sb.append("2. 典型的探索路径：list_database_objects(schemas) → list_database_objects(tables) → list_database_objects(columns)。\n");
        sb.append("3. 写出 SQL 后，调用 validate_sql 自检；若有语法或字段错误，依据报错修正后重新校验。\n");
        sb.append("4. 仅在确有必要时调用 preview_sql_result 试跑，用于确认口径，不要滥用。\n");
        sb.append("5. 确认无误后直接给出最终答案，不必再有冗长铺垫。\n\n");
        sb.append("硬性约束：\n");
        sb.append("- 只生成单条只读 SELECT 查询，禁止 INSERT/UPDATE/DELETE/DDL 等任何写操作。\n");
        sb.append("- 严禁编造不存在的表名或字段名，所有标识符必须来自工具查询的真实元数据。\n");
        sb.append("- SQL 必须适配当前数据源的方言。\n");
        sb.append("- 输出用简体中文说明，SQL 放在 ```sql 代码块中。\n\n");
        appendDataSourceHint(sb, dataSource, "数据查询页");
        return sb.toString();
    }

    private static void appendDataSourceHint(StringBuilder sb, DataSourceDTO dataSource, String page) {
        if (dataSource != null) {
            sb.append("当前数据源信息：\n");
            sb.append("- 名称：").append(nullSafe(dataSource.getName())).append('\n');
            sb.append("- 类型：").append(nullSafe(dataSource.getType())).append('\n');
            if (dataSource.getSchemaName() != null && !dataSource.getSchemaName().isBlank()) {
                sb.append("- 默认 schema：").append(dataSource.getSchemaName()).append('\n');
            }
            sb.append("- 数据源已在会话上下文中绑定，调用工具时无需传入数据源标识。\n");
        } else {
            sb.append("本次会话未绑定具体数据源，若用户需要真实表结构，请提示其在").append(page).append("选择数据源后再试。\n");
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "-" : value;
    }
}
