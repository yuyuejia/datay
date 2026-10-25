import { applyDashboardFilters, distinctFieldValues, filtersFromQuery, initFilterValues } from './filter-utils';
import type { DashboardFilter, DatasetResult } from './types';

const filters: DashboardFilter[] = [
  { id: 'region', type: 'select', defaultValue: '华东' },
  { id: 'cities', type: 'multiSelect' },
  { id: 'date', type: 'dateRange' },
  { id: 'keyword', type: 'input' },
];

describe('filter-utils', () => {
  it('initializes defaults by filter type', () => {
    expect(initFilterValues(filters)).toEqual({
      region: '华东',
      cities: [],
      date: null,
      keyword: undefined,
    });
  });

  it('restores values from query string', () => {
    expect(filtersFromQuery(filters, '?f_region=华南&f_cities=广州,深圳&f_keyword=手机')).toEqual({
      region: '华南',
      cities: ['广州', '深圳'],
      keyword: '手机',
    });
  });

  it('ignores unknown and missing query params', () => {
    expect(filtersFromQuery(filters, '?other=1')).toEqual({});
    expect(filtersFromQuery(undefined, '?f_region=华南')).toEqual({});
  });

  it('restores range filters from query string', () => {
    const rangeFilters: DashboardFilter[] = [
      { id: 'date', type: 'dateRange' },
      { id: 'amount', type: 'numberRange' },
    ];

    expect(filtersFromQuery(rangeFilters, '?f_date=2026-10-01,2026-10-31&f_amount=100,200')).toEqual({
      date: { start: '2026-10-01', end: '2026-10-31' },
      amount: { min: 100, max: 200 },
    });
    expect(filtersFromQuery(rangeFilters, '?f_date=2026-10-01~2026-10-31&f_amount=100~200')).toEqual({
      date: { start: '2026-10-01', end: '2026-10-31' },
      amount: { min: 100, max: 200 },
    });
    expect(filtersFromQuery(rangeFilters, '?f_date=2026-10-01')).toEqual({
      date: { start: '2026-10-01' },
    });
  });

  it('restores JSON range and multi-select values from query string', () => {
    const jsonFilters: DashboardFilter[] = [
      { id: 'date', type: 'dateRange' },
      { id: 'amount', type: 'numberRange' },
      { id: 'cities', type: 'multiSelect' },
    ];
    const search =
      '?f_date=' +
      encodeURIComponent('["2026-10-01","2026-10-31"]') +
      '&f_amount=' +
      encodeURIComponent('{"min":100,"max":200}') +
      '&f_cities=' +
      encodeURIComponent('["广州","深圳"]');

    expect(filtersFromQuery(jsonFilters, search)).toEqual({
      date: { start: '2026-10-01', end: '2026-10-31' },
      amount: { min: 100, max: 200 },
      cities: ['广州', '深圳'],
    });
  });

  it('drops malformed range values instead of leaking strings into filters', () => {
    const rangeFilters: DashboardFilter[] = [{ id: 'amount', type: 'numberRange' }];
    expect(filtersFromQuery(rangeFilters, '?f_amount=not-a-number')).toEqual({ amount: null });
  });
});

const result: DatasetResult = {
  columns: ['region', 'amount', 'date', 'channel'],
  rows: [
    { region: '华东', amount: 120, date: '2026-10-01', channel: '官网' },
    { region: '华南', amount: 80, date: '2026-10-08', channel: 'APP' },
    { region: '华东', amount: 60, date: '2026-10-15', channel: '门店' },
  ],
};

describe('filter-utils field filters', () => {
  it('applies select, multiSelect, input, date and number filters to one dataset', () => {
    const target = (field: string) => [{ datasetId: 'ds1', field, mode: 'client' as const }];
    const filters: DashboardFilter[] = [
      { id: 'region', type: 'select', targets: target('region') },
      { id: 'channels', type: 'multiSelect', targets: target('channel') },
      { id: 'keyword', type: 'input', targets: target('region') },
      { id: 'date', type: 'dateRange', targets: target('date') },
      { id: 'amount', type: 'numberRange', targets: target('amount') },
    ];

    const filtered = applyDashboardFilters(
      result,
      filters,
      {
        region: '华东',
        channels: ['官网', '门店'],
        keyword: '华',
        date: { start: '2026-10-01', end: '2026-10-10' },
        amount: { min: 100, max: 130 },
      },
      'ds1',
    );

    expect(filtered.rows).toEqual([{ region: '华东', amount: 120, date: '2026-10-01', channel: '官网' }]);
    expect(filtered.affectedRows).toBe(1);
  });

  it('ignores empty values, other datasets and pushdown targets', () => {
    const filters: DashboardFilter[] = [
      { id: 'region', type: 'select', targets: [{ datasetId: 'ds1', field: 'region', mode: 'client' }] },
      { id: 'other', type: 'select', targets: [{ datasetId: 'ds2', field: 'region', mode: 'client' }] },
      {
        id: 'old',
        type: 'select',
        targets: [{ datasetId: 'ds1', mode: 'server', pushdown: { kind: 'sqlParam', param: 'region' } }],
      },
    ];

    expect(applyDashboardFilters(result, filters, { region: undefined, other: '华南', old: '华南' }, 'ds1').rows).toHaveLength(3);
  });

  it('extracts distinct options from a field', () => {
    expect(distinctFieldValues(result, 'region')).toEqual(['华东', '华南']);
    expect(distinctFieldValues(result, 'missing')).toEqual([]);
  });

  it('throws a diagnosable error when a filter field is absent from result columns', () => {
    const filters: DashboardFilter[] = [
      { id: 'missing', type: 'select', targets: [{ datasetId: 'ds1', field: 'not_a_column', mode: 'client' }] },
    ];

    expect(() => applyDashboardFilters(result, filters, { missing: 'x' }, 'ds1')).toThrow(
      '筛选器 missing 的字段 not_a_column 不在数据集 ds1 的查询结果中',
    );
  });
});
