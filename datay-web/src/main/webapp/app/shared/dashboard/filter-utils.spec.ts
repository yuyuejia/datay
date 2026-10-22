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
    const filters: DashboardFilter[] = [
      { id: 'region', type: 'select', datasetId: 'ds1', field: 'region' },
      { id: 'channels', type: 'multiSelect', datasetId: 'ds1', field: 'channel' },
      { id: 'keyword', type: 'input', datasetId: 'ds1', field: 'region' },
      { id: 'date', type: 'dateRange', datasetId: 'ds1', field: 'date' },
      { id: 'amount', type: 'numberRange', datasetId: 'ds1', field: 'amount' },
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

  it('ignores empty values, other datasets and legacy bindings', () => {
    const filters: DashboardFilter[] = [
      { id: 'region', type: 'select', datasetId: 'ds1', field: 'region' },
      { id: 'other', type: 'select', datasetId: 'ds2', field: 'region' },
      { id: 'old', type: 'select', datasetId: 'ds1', field: 'region', bindings: [{ datasetId: 'ds1', kind: 'sqlParam', param: 'region' }] },
    ];

    expect(applyDashboardFilters(result, filters, { region: undefined, other: '华南', old: '华南' }, 'ds1').rows).toHaveLength(3);
  });

  it('extracts distinct options from a field', () => {
    expect(distinctFieldValues(result, 'region')).toEqual(['华东', '华南']);
    expect(distinctFieldValues(result, 'missing')).toEqual([]);
  });
});
