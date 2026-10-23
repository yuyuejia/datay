import { buildDatasetDefinition } from './analysis-dataset-edit.component';
import type { DimensionOption } from './analysis-dataset-edit.component';

const dimension = (fieldNames: string[]): DimensionOption => ({
  dimensionModelCode: 'dim_category',
  dimensionModelName: '商品分类',
  selected: true,
  fieldNames,
  fields: [
    { fieldName: 'level1_name', description: '一级分类' },
    { fieldName: 'level2_name', description: '二级分类' },
  ],
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
});
