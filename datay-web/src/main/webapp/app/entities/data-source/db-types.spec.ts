import { describe, expect, it } from 'vitest';

import { DB_TYPE_CATEGORY, dbTypeCategories, dbTypes, filterDbTypes } from './db-types';

describe('db-types categories', () => {
  it('exposes the requested category filters', () => {
    const labels = dbTypeCategories.map(category => category.label);
    expect(labels).toEqual(expect.arrayContaining(['全部', '分析型数据库', '轻量数据库', '时序数据库', '文档检索']));
    expect(labels).not.toContain('数仓分析');
  });

  it('returns all types when no category is selected', () => {
    expect(filterDbTypes()).toHaveLength(dbTypes.length);
    expect(filterDbTypes(DB_TYPE_CATEGORY.ALL)).toHaveLength(dbTypes.length);
  });

  it('filters time-series databases', () => {
    const names = filterDbTypes(DB_TYPE_CATEGORY.TIMESERIES).map(type => type.name);
    expect(names).toEqual(expect.arrayContaining(['TDENGINE', 'INFLUXDB']));
    expect(names).not.toContain('MYSQL');
  });

  it('filters document and search databases', () => {
    const names = filterDbTypes(DB_TYPE_CATEGORY.DOCUMENT).map(type => type.name);
    expect(names).toEqual(expect.arrayContaining(['MONGODB', 'ELASTICSEARCH']));
  });

  it('filters lightweight databases', () => {
    const names = filterDbTypes(DB_TYPE_CATEGORY.LIGHTWEIGHT).map(type => type.name);
    expect(names).toEqual(expect.arrayContaining(['DUCKDB', 'SQLITE']));
  });

  it('filters analytical databases', () => {
    const analytical = filterDbTypes(DB_TYPE_CATEGORY.ANALYTICAL).map(type => type.name);
    expect(analytical).toEqual(expect.arrayContaining(['CLICKHOUSE', 'DORIS', 'DUCKDB', 'STARROCKS', 'GREENPLUM', 'POLARIS']));
  });

  it('filters lakehouse catalogs', () => {
    const lakehouse = filterDbTypes(DB_TYPE_CATEGORY.LAKEHOUSE).map(type => type.name);
    expect(lakehouse).toEqual(expect.arrayContaining(['DUCKLAKE', 'POLARIS']));
  });

  it('labels the Polaris warehouse field', () => {
    const polaris = dbTypes.find(type => type.name === 'POLARIS');
    expect(polaris).toBeDefined();
    expect(polaris!.databaseLabel).toBe('Warehouse');
    expect(polaris!.defaultPort).toBe('8181');
  });

  it('configures YMatrix as a Greenplum-compatible time-series database', () => {
    const ymatrix = dbTypes.find(type => type.name === 'YMATRIX');
    expect(ymatrix).toBeDefined();
    expect(ymatrix!.jdbcUrlTemplate).toBe('jdbc:postgresql://{host}:{port}/{database}');
    expect(ymatrix!.defaultPort).toBe('5432');
    expect(ymatrix!.schemaEnabled).toBe(true);
    expect(ymatrix!.tags).toEqual(expect.arrayContaining([DB_TYPE_CATEGORY.ANALYTICAL, DB_TYPE_CATEGORY.TIMESERIES]));
  });

  it('configures KingbaseES as a PostgreSQL-compatible relational database', () => {
    const kingbase = dbTypes.find(type => type.name === 'KINGBASE');
    expect(kingbase).toBeDefined();
    expect(kingbase!.displayName).toBe('人大金仓 KingbaseES');
    expect(kingbase!.jdbcUrlTemplate).toBe('jdbc:kingbase8://{host}:{port}/{database}');
    expect(kingbase!.defaultPort).toBe('54321');
    expect(kingbase!.schemaEnabled).toBe(true);
    expect(kingbase!.tags).toEqual(expect.arrayContaining([DB_TYPE_CATEGORY.RELATIONAL]));
  });

  it('configures Redis as a non-JDBC key-value datasource', () => {
    const redis = dbTypes.find(type => type.name === 'REDIS');
    expect(redis).toBeDefined();
    expect(redis!.jdbcUrlTemplate).toBe('redis://{host}:{port}');
    expect(redis!.defaultPort).toBe('6379');
    expect(redis!.schemaEnabled).toBe(true);
    expect(redis!.databaseEnabled).toBe(false);
    expect(redis!.tags).toEqual(expect.arrayContaining([DB_TYPE_CATEGORY.OTHER]));
  });

  it('assigns at least one known category tag to every type', () => {
    const knownTags = new Set(dbTypeCategories.map(category => category.value));
    dbTypes.forEach(type => {
      expect(type.tags, `${type.name} 缺少分类标签`).toBeDefined();
      expect(type.tags!.length, `${type.name} 分类标签为空`).toBeGreaterThan(0);
      type.tags!.forEach(tag => expect(knownTags.has(tag), `${type.name} 使用了未知分类 ${tag}`).toBe(true));
    });
  });
});
