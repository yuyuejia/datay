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

/**
 * 组装限定表名。
 *
 * <p>部分数据源会把同一个命名空间同时暴露为 catalog 和 schema（例如 ClickHouse 把 database
 * 同时作为 catalog 与 schema），此时只能生成 {@code database.table}，不能生成三段
 * {@code catalog.schema.table}，否则 ClickHouse 会把第三段解析成表名的一部分并报语法错误。</p>
 *
 * @param quote   标识符引用方法
 * @param catalog catalog / database 名，可为空
 * @param schema  schema 名，可为空；与 catalog 相同则忽略
 * @param table   表名
 */
export function qualifiedTableName(
  quote: (identifier: string) => string,
  catalog?: string | null,
  schema?: string | null,
  table?: string | null,
): string {
  const parts: string[] = [];
  if (catalog) {
    parts.push(catalog);
  }
  if (schema && (!catalog || schema.toUpperCase() !== catalog.toUpperCase())) {
    parts.push(schema);
  }
  if (table) {
    parts.push(table);
  }
  return parts.map(quote).join('.');
}

const BACKTICK_TYPES = ['MYSQL', 'TIDB', 'DORIS', 'CLICKHOUSE', 'TDENGINE'];
const BRACKET_TYPES = ['SQLSERVER', 'SQL_SERVER', 'MSSQL'];

/** SQL 编辑器支持的方言标识。 */
export type SqlEditorDialect = 'duckdb' | 'mariadb' | 'mysql' | 'plsql' | 'postgresql' | 'sql' | 'transactsql';

/**
 * 根据数据源类型返回 SQL 编辑器的方言，用于关键字高亮与格式化。
 *
 * @param type 数据源类型，如 SQLSERVER、MYSQL、POSTGRESQL、DUCKDB、ORACLE
 */
export function getSqlEditorDialect(type?: string | null): SqlEditorDialect {
  const normalized = (type || '').toUpperCase();
  if (normalized.includes('MARIADB')) {
    return 'mariadb';
  }
  if (BACKTICK_TYPES.some(t => normalized.includes(t))) {
    return 'mysql';
  }
  if (normalized.includes('DUCKDB') || normalized.includes('DUCKLAKE')) {
    return 'duckdb';
  }
  if (
    normalized.includes('POSTGRES') ||
    normalized.includes('POLARIS') ||
    normalized.includes('GREENPLUM') ||
    normalized.includes('YMATRIX') ||
    normalized.includes('KINGBASE')
  ) {
    return 'postgresql';
  }
  if (BRACKET_TYPES.some(t => normalized.includes(t))) {
    return 'transactsql';
  }
  if (normalized.includes('ORACLE')) {
    return 'plsql';
  }
  return 'sql';
}

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
