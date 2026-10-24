import { describe, expect, it } from 'vitest';

import { getSqlDialect } from './sql-dialect';

describe('getSqlDialect', () => {
  it('SQL Server 使用方括号并生成 SELECT TOP 语句', () => {
    const dialect = getSqlDialect('SQLSERVER');
    expect(dialect.quote('dbo')).toBe('[dbo]');
    expect(dialect.limit('SELECT * FROM [dbo].[customers]', 100)).toBe('SELECT TOP 100 * FROM [dbo].[customers];');
  });

  it('兼容 Mssql / SQL_SERVER 类型别名', () => {
    expect(getSqlDialect('MSSQL').limit('SELECT * FROM t', 10)).toBe('SELECT TOP 10 * FROM t;');
    expect(getSqlDialect('SQL_SERVER').limit('select * from t', 10)).toBe('SELECT TOP 10 * from t;');
  });

  it('MySQL 使用反引号并追加 LIMIT', () => {
    const dialect = getSqlDialect('MYSQL');
    expect(dialect.quote('t')).toBe('`t`');
    expect(dialect.limit('SELECT * FROM `db`.`t`', 100)).toBe('SELECT * FROM `db`.`t` LIMIT 100;');
  });

  it('Oracle 使用 FETCH FIRST', () => {
    expect(getSqlDialect('ORACLE').limit('SELECT * FROM "T"', 50)).toBe('SELECT * FROM "T" FETCH FIRST 50 ROWS ONLY;');
  });

  it('PostgreSQL / DuckDB 使用双引号并追加 LIMIT', () => {
    const dialect = getSqlDialect('POSTGRESQL');
    expect(dialect.quote('public')).toBe('"public"');
    expect(dialect.limit('SELECT * FROM "public"."t"', 20)).toBe('SELECT * FROM "public"."t" LIMIT 20;');
    expect(getSqlDialect('DUCKDB').limit('SELECT * FROM t', 20)).toBe('SELECT * FROM t LIMIT 20;');
  });
});
