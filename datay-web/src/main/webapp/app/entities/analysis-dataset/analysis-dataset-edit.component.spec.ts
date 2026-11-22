import { buildDatasetDefinition, buildMetricFilterConfig } from './analysis-dataset-edit.component';
import type { DimensionOption, MetricFilterConditionOption } from './analysis-dataset-edit.component';

const dimension = (fieldNames: string[]): DimensionOption => ({
  dimensionModelCode: 'dim_category',
  dimensionModelName: '商品分类',
  selected: true,
  fieldNames,
  fields: [
    { fieldName: 'level1_name', description: '一级分类' },
    { fieldName: 'level2_name', description: '二级分类' },
  ],
  roles: [],
  selectedRoles: [],
});

const condition = (overrides: Partial<MetricFilterConditionOption> = {}): MetricFilterConditionOption => ({
  dimensionModelCode: 'dim_store',
  dimensionRole: '',
  dimensionFieldName: 'region',
  operator: 'EQ',
  value: '华东',
  valueEnd: '',
  logic: 'AND',
  ...overrides,
});

describe('analysis dataset edit', () => {
  it('saves multiple display fields for a dimension', () => {
    const definition = buildDatasetDefinition('METRIC', '', ['sales_amount'], [dimension(['level1_name', 'level2_name'])]);

    expect(JSON.parse(definition)).toMatchObject({
      metricQuery: {
        metricCodes: ['sales_amount'],
        dimensions: [
          {
            dimensionModelCode: 'dim_category',
            dimensionFieldNames: ['level1_name', 'level2_name'],
          },
        ],
      },
    });
  });

  it('omits empty display field lists', () => {
    const definition = buildDatasetDefinition('METRIC', '', ['sales_amount'], [dimension([])]);

    expect(JSON.parse(definition).metricQuery.dimensions[0].dimensionFieldNames).toBeUndefined();
  });

  it('saves hierarchy level selection', () => {
    const definition = buildDatasetDefinition('METRIC', '', ['sales_amount'], [{ ...dimension([]), isHierarchy: true, levelIndex: 3 }]);

    expect(JSON.parse(definition).metricQuery.dimensions[0]).toMatchObject({
      dimensionModelCode: 'dim_category',
      levelIndex: 3,
    });
  });

  it('expands the same dimension into multiple role selections', () => {
    const definition = buildDatasetDefinition('METRIC', '', ['sales_amount'], [
      {
        ...dimension(['year', 'month']),
        dimensionModelCode: 'dim_date',
        dimensionModelName: '日期维度',
        roles: [
          { code: 'ORDER_DATE', name: '下单日期' },
          { code: 'PAY_DATE', name: '支付日期' },
        ],
        selectedRoles: ['ORDER_DATE', 'PAY_DATE'],
      },
    ]);

    expect(JSON.parse(definition).metricQuery.dimensions).toEqual([
      {
        dimensionModelCode: 'dim_date',
        dimensionRole: 'ORDER_DATE',
        dimensionFieldNames: ['year', 'month'],
      },
      {
        dimensionModelCode: 'dim_date',
        dimensionRole: 'PAY_DATE',
        dimensionFieldNames: ['year', 'month'],
      },
    ]);
  });

  it('saves business filters and time range', () => {
    const filterConfig = buildMetricFilterConfig([
      condition(),
      condition({ dimensionFieldName: 'city', operator: 'BETWEEN', value: '2026-01-01', valueEnd: '2026-01-31', logic: 'OR' }),
    ]);
    const definition = buildDatasetDefinition('METRIC', '', ['sales_amount'], [dimension(['level1_name'])], undefined, filterConfig, {
      start: '2026-01-01T00:00:00',
      end: '2026-01-31T23:59:59',
    });

    expect(JSON.parse(definition).metricQuery).toMatchObject({
      filterConfig: {
        conditions: [
          {
            type: 'DIMENSION',
            dimensionModelCode: 'dim_store',
            dimensionFieldName: 'region',
            operator: 'EQ',
            value: '华东',
            logic: 'AND',
          },
          {
            dimensionModelCode: 'dim_store',
            dimensionFieldName: 'city',
            operator: 'BETWEEN',
            value: '2026-01-01',
            valueEnd: '2026-01-31',
            logic: 'OR',
          },
        ],
      },
      timeRange: {
        start: '2026-01-01T00:00:00',
        end: '2026-01-31T23:59:59',
      },
    });
  });

  it('drops incomplete filter conditions', () => {
    expect(buildMetricFilterConfig([condition({ dimensionFieldName: '' })])).toBeUndefined();
  });
});
