import { shallowMount } from '@vue/test-utils';

import type { IETLComponentField } from '@/shared/model/etl-component.model';

import DynamicField from './DynamicField.vue';

const createWrapper = (field: IETLComponentField, modelValue: any = []) =>
  shallowMount(DynamicField, {
    props: {
      field,
      modelValue,
      formValues: { sourceId: 'ds1', schema: 'demo' },
      component: {},
    },
  });

describe('DynamicField', () => {
  it('多选表时向上传递数组，由表单层统一序列化', () => {
    const field: IETLComponentField = {
      key: 'table',
      type: 'TABLE',
      multiple: true,
      valueFormat: 'COMMA_STRING',
      dataSourceField: 'sourceId',
      schemaField: 'schema',
    };
    const wrapper = createWrapper(field);

    wrapper.findComponent({ name: 'DataSourceTableSelector' }).vm.$emit('selected', [{ table: 'orders' }, { table: 'order_items' }]);

    expect(wrapper.emitted('change')?.[0]?.[0]).toEqual({ value: ['orders', 'order_items'] });
  });

  it('多选字段时向上传递数组', () => {
    const field: IETLComponentField = {
      key: 'columns',
      type: 'COLUMN',
      multiple: true,
      valueFormat: 'COMMA_STRING',
      dataSourceField: 'sourceId',
      schemaField: 'schema',
      tableField: 'table',
    };
    const wrapper = createWrapper(field);

    wrapper.findComponent({ name: 'DataSourceTableFieldSelector' }).vm.$emit('selected', [{ name: 'id' }, { name: 'name' }]);

    expect(wrapper.emitted('change')?.[0]?.[0]).toEqual({ value: ['id', 'name'] });
  });
});
