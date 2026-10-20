package com.data.datafusion.service.lineage;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SQL 血缘解析器：提取 SQL 读取与写入的物理表。
 *
 * <p>优先使用 JSqlParser 解析 AST，可正确处理 CTE、子查询与多表 JOIN。部分数据库方言
 * （如 DuckDB 的 read_parquet、特殊 DDL）无法被 JSqlParser 解析时，回退到轻量 token
 * 扫描，并通过 {@link SqlReferences#inferred()} 标记结果为推断值。</p>
 */
@Component
public class SqlLineageParser {

    private static final Logger LOG = LoggerFactory.getLogger(SqlLineageParser.class);

    private static final String IDENTIFIER =
        "(?:[A-Za-z_$][A-Za-z0-9_$]*|\"[^\"]+\"|`[^`]+`|\\[[^\\]]+\\])" +
        "(?:\\s*\\.\\s*(?:[A-Za-z_$][A-Za-z0-9_$]*|\"[^\"]+\"|`[^`]+`|\\[[^\\]]+\\])){0,2}";

    private static final Pattern WRITE_PATTERN = Pattern.compile(
        "(?is)\\b(?:insert\\s+(?:into|overwrite)\\s+|replace\\s+into\\s+|merge\\s+into\\s+|update\\s+|delete\\s+from\\s+" +
        "|truncate\\s+(?:table\\s+)?|alter\\s+table\\s+|drop\\s+table\\s+(?:if\\s+exists\\s+)?" +
        "|create\\s+(?:or\\s+replace\\s+)?(?:temporary\\s+|temp\\s+)?table\\s+(?:if\\s+not\\s+exists\\s+)?)(" +
        IDENTIFIER +
        ")"
    );

    private static final Pattern READ_PATTERN = Pattern.compile("(?is)\\b(?:from|join|using)\\s+(" + IDENTIFIER + ")");

    private static final Pattern CTE_PATTERN = Pattern.compile(
        "(?is)(?:\\bwith\\b|,)\\s*(" + IDENTIFIER + ")\\s+as\\s*\\("
    );

    public SqlReferences parse(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlReferences.empty();
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            Set<TableRef> reads = new LinkedHashSet<>();
            Set<TableRef> writes = new LinkedHashSet<>();
            for (Statement statement : statements.getStatements()) {
                collect(statement, reads, writes);
            }
            return new SqlReferences(reads, writes, false);
        } catch (Exception e) {
            LOG.debug("JSqlParser 解析失败，回退到轻量 SQL 扫描: {}", e.getMessage());
            return fallbackParse(sql);
        }
    }

    private void collect(Statement statement, Set<TableRef> reads, Set<TableRef> writes) {
        Set<TableRef> statementWrites = writeTables(statement);
        writes.addAll(statementWrites);
        Set<TableRef> allTables = new LinkedHashSet<>();
        try {
            TablesNamesFinder<Void> finder = new TablesNamesFinder<>();
            for (String table : finder.getTables(statement)) {
                allTables.add(TableRef.parse(table));
            }
        } catch (RuntimeException e) {
            LOG.debug("提取 SQL 表名失败: {}", e.getMessage());
        }
        for (TableRef table : allTables) {
            if (statementWrites.stream().noneMatch(write -> sameTable(table, write))) {
                reads.add(table);
            }
        }
    }

    private Set<TableRef> writeTables(Statement statement) {
        Set<TableRef> tables = new LinkedHashSet<>();
        if (statement instanceof Insert insert) {
            addTable(tables, insert.getTable());
        } else if (statement instanceof CreateTable createTable) {
            addTable(tables, createTable.getTable());
        } else if (statement instanceof Update update) {
            addTable(tables, update.getTable());
        } else if (statement instanceof Delete delete) {
            addTable(tables, delete.getTable());
        } else if (statement instanceof Merge merge) {
            addTable(tables, merge.getTable());
        } else if (statement instanceof Truncate truncate) {
            addTable(tables, truncate.getTable());
        } else if (statement instanceof Alter alter) {
            addTable(tables, alter.getTable());
        } else if (statement instanceof Drop drop) {
            addTable(tables, drop.getName());
        }
        return tables;
    }

    private void addTable(Set<TableRef> tables, Table table) {
        if (table == null) {
            return;
        }
        String name = table.getFullyQualifiedName();
        if (name != null && !name.isBlank()) {
            tables.add(TableRef.parse(name));
        }
    }

    private boolean sameTable(TableRef left, TableRef right) {
        return TableRef.same(left.table(), right.table()) &&
        TableRef.same(left.schema(), right.schema()) &&
        TableRef.same(left.catalog(), right.catalog());
    }

    private SqlReferences fallbackParse(String sql) {
        String cleaned = stripCommentsAndLiterals(sql);
        Set<TableRef> writes = new LinkedHashSet<>();
        Set<TableRef> reads = new LinkedHashSet<>();
        Set<TableRef> ctes = extractCtes(cleaned);

        Matcher writeMatcher = WRITE_PATTERN.matcher(cleaned);
        while (writeMatcher.find()) {
            TableRef table = TableRef.parse(writeMatcher.group(1));
            if (!isCte(table, ctes)) {
                writes.add(table);
            }
        }

        Matcher readMatcher = READ_PATTERN.matcher(cleaned);
        while (readMatcher.find()) {
            TableRef table = TableRef.parse(readMatcher.group(1));
            if (!isCte(table, ctes) && writes.stream().noneMatch(write -> sameTable(table, write))) {
                reads.add(table);
            }
        }
        return new SqlReferences(reads, writes, true);
    }

    private Set<TableRef> extractCtes(String sql) {
        Set<TableRef> ctes = new LinkedHashSet<>();
        Matcher matcher = CTE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isBlank()) {
                ctes.add(TableRef.parse(value));
            }
        }
        return ctes;
    }

    private boolean isCte(TableRef table, Set<TableRef> ctes) {
        return table.schema() == null && ctes.stream().anyMatch(cte -> TableRef.same(table.table(), cte.table()));
    }

    /**
     * 去掉注释，并把单引号字符串内容替换为空格；双引号、反引号、方括号作为标识符保留。
     */
    String stripCommentsAndLiterals(String sql) {
        if (sql == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(sql.length());
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        boolean bracket = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                if (current == '\n') {
                    lineComment = false;
                    result.append(current);
                } else {
                    result.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    result.append("  ");
                    i++;
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (singleQuote) {
                if (current == '\'' && next == '\'') {
                    result.append("  ");
                    i++;
                } else if (current == '\'') {
                    singleQuote = false;
                    result.append(' ');
                } else {
                    result.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (doubleQuote) {
                result.append(current);
                if (current == '"' && next == '"') {
                    result.append(next);
                    i++;
                } else if (current == '"') {
                    doubleQuote = false;
                }
                continue;
            }
            if (backtick) {
                result.append(current);
                if (current == '`') {
                    backtick = false;
                }
                continue;
            }
            if (bracket) {
                result.append(current);
                if (current == ']') {
                    bracket = false;
                }
                continue;
            }

            if (current == '-' && next == '-') {
                lineComment = true;
                result.append("  ");
                i++;
            } else if (current == '#') {
                lineComment = true;
                result.append(' ');
            } else if (current == '/' && next == '*') {
                blockComment = true;
                result.append("  ");
                i++;
            } else if (current == '\'') {
                singleQuote = true;
                result.append(' ');
            } else if (current == '"') {
                doubleQuote = true;
                result.append(current);
            } else if (current == '`') {
                backtick = true;
                result.append(current);
            } else if (current == '[') {
                bracket = true;
                result.append(current);
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    /**
     * 根据数据源类型返回 JSqlParser 可识别的方言标识，供后续扩展。当前 JSqlParser 自动识别常见语法。
     */
    public String normalizeDialect(String dataSourceType) {
        return dataSourceType == null ? "" : dataSourceType.trim().toLowerCase(Locale.ROOT);
    }
}
