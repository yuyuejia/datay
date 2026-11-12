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
    expect(analytical).toEqual(expect.arrayContaining(['CLICKHOUSE', 'DORIS', 'DUCKDB', 'STARROCKS', 'GREENPLUM']));
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
