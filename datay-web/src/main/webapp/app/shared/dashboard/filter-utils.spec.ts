import { filtersFromQuery, initFilterValues } from './filter-utils';
import type { DashboardFilter } from './types';

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
