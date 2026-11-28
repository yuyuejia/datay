import { shallowMount } from '@vue/test-utils';

import type { IETLComponentField } from '@/shared/model/etl-component.model';

import SqlEditor from '@/components/SqlEditor.vue';
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

  it('SQL 多行文本字段使用 SQL 编辑器', () => {
    const wrapper = createWrapper({ key: 'sql', label: 'SQL语句', type: 'TEXTAREA' }, 'SELECT 1');

    expect(wrapper.findComponent(SqlEditor).exists()).toBe(true);
    expect(wrapper.find('textarea').exists()).toBe(false);
  });

  it('非 SQL 多行文本字段仍使用普通文本域', () => {
    const wrapper = createWrapper({ key: 'remark', label: '备注', type: 'TEXTAREA' }, '');

    expect(wrapper.findComponent(SqlEditor).exists()).toBe(false);
    expect(wrapper.find('el-input-stub').exists()).toBe(true);
  });
});
