/**
 * 各数据库方言的 SQL 生成辅助。
 *
 * <p>不同数据库的标识符引用与行数限制语法差异较大：
 * <ul>
 *   <li>MySQL / TiDB / Doris / ClickHouse：反引号 + {@code LIMIT n}</li>
 *   <li>SQL Server：方括号 + {@code SELECT TOP n}</li>
 *   <li>Oracle：双引号 + {@code FETCH FIRST n ROWS ONLY}</li>
 *   <li>PostgreSQL / DuckDB 等：双引号 + {@code LIMIT n}</li>
 * </ul>
 */
export interface SqlDialect {
  /** 引用标识符（表名、字段名、schema 名）。 */
  quote: (identifier: string) => string;
  /** 为查询语句追加行数限制。 */
  limit: (baseSql: string, limit: number) => string;
}

const BACKTICK_TYPES = ['MYSQL', 'TIDB', 'DORIS', 'CLICKHOUSE'];
const BRACKET_TYPES = ['SQLSERVER', 'SQL_SERVER', 'MSSQL'];

/**
 * 根据数据源类型返回对应的方言辅助方法。
 *
 * @param type 数据源类型，如 SQLSERVER、MYSQL、POSTGRESQL、ORACLE
 */
export function getSqlDialect(type?: string | null): SqlDialect {
  const normalized = (type || '').toUpperCase();
  const isBacktick = BACKTICK_TYPES.some(t => normalized.includes(t));
  const isBracket = BRACKET_TYPES.some(t => normalized.includes(t));
  const isOracle = normalized.includes('ORACLE');

  return {
    quote: (identifier: string) => {
      if (isBacktick) return `\`${identifier}\``;
      if (isBracket) return `[${identifier}]`;
      return `"${identifier}"`;
    },
    limit: (baseSql: string, limit: number) => {
      if (isOracle) {
        return `${baseSql} FETCH FIRST ${limit} ROWS ONLY;`;
      }
      if (isBracket) {
        // SQL Server 不支持 LIMIT，需要使用 SELECT TOP n；需插到 SELECT 之后
        return `${baseSql.replace(/^\s*SELECT\s+/i, `SELECT TOP ${limit} `)};`;
      }
      return `${baseSql} LIMIT ${limit};`;
    },
  };
}
