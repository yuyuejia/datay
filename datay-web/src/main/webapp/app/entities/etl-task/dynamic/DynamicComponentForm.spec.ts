import { mount } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';

import type { IETLComponentField } from '@/shared/model/etl-component.model';

import DynamicComponentForm from './DynamicComponentForm.vue';

const DynamicFieldStub = defineComponent({
  name: 'DynamicField',
  props: {
    field: { type: Object, required: true },
    modelValue: { type: [String, Number, Boolean, Array, Object] as any, default: undefined },
    formValues: { type: Object, required: true },
  },
  emits: ['change'],
  template: '<div class="dynamic-field-stub" />',
});

const fields: IETLComponentField[] = [
  {
    key: 'sourceId',
    label: '数据源',
    type: 'DATASOURCE',
    group: '基础配置',
    order: 10,
    required: true,
    schemaField: 'schema',
  },
  {
    key: 'schema',
    label: 'Schema',
    type: 'SCHEMA',
    group: '基础配置',
    order: 20,
  },
  {
    key: 'table',
    label: '表名',
    type: 'TABLE',
    group: '基础配置',
    order: 30,
    multiple: true,
    valueFormat: 'COMMA_STRING',
  },
  {
    key: 'batchSize',
    label: '批量大小',
    type: 'NUMBER',
    group: '基础配置',
    order: 40,
    defaultValue: '500',
  },
  {
    key: 'enabled',
    label: '启用',
    type: 'SWITCH',
    group: '基础配置',
    order: 50,
    defaultValue: 'true',
  },
  {
    key: 'eventTypes',
    label: '事件类型',
    type: 'MULTI_SELECT',
    group: '基础配置',
    order: 60,
    valueFormat: 'ARRAY',
    options: [
      { value: 'INSERT', label: 'INSERT' },
      { value: 'UPDATE', label: 'UPDATE' },
    ],
  },
];

const createWrapper = (config: Record<string, any> = {}) =>
  mount(DynamicComponentForm, {
    props: {
      node: {
        data: {
          config: {
            sourceId: 'ds1',
            schema: 'public',
            table: 't1,t2',
            batchSize: '10',
            enabled: false,
            unknownKey: 'keep',
            ...config,
          },
        },
      },
      fields,
    },
    global: {
      stubs: {
        DynamicField: DynamicFieldStub,
      },
    },
  });

describe('DynamicComponentForm', () => {
  it('初始化时按字段类型解析现有配置', async () => {
    const wrapper = createWrapper();
    await nextTick();

    const fieldComponents = wrapper.findAllComponents(DynamicFieldStub);
    expect(fieldComponents).toHaveLength(fields.length);
    expect(fieldComponents[0].props('modelValue')).toBe('ds1');
    expect(fieldComponents[2].props('modelValue')).toEqual(['t1', 't2']);
    expect(fieldComponents[3].props('modelValue')).toBe(10);
    expect(fieldComponents[4].props('modelValue')).toBe(false);
    expect(fieldComponents[5].props('modelValue')).toEqual([]);
  });

  it('保存时按 valueFormat 序列化并保留未知配置', async () => {
    const wrapper = createWrapper();
    await nextTick();

    const fieldComponents = wrapper.findAllComponents(DynamicFieldStub);
    fieldComponents[5].vm.$emit('change', { value: ['INSERT', 'UPDATE'] });
    await nextTick();

    await (wrapper.vm as any).saveConfig();

    const saved = wrapper.emitted('save')?.[0]?.[0] as Record<string, any>;
    expect(saved).toMatchObject({
      sourceId: 'ds1',
      schema: 'public',
      table: 't1,t2',
      batchSize: 10,
      enabled: false,
      eventTypes: ['INSERT', 'UPDATE'],
      unknownKey: 'keep',
    });
  });

  it('数据源变更时清空下游表和字段选择', async () => {
    const wrapper = createWrapper();
    await nextTick();

    const fieldComponents = wrapper.findAllComponents(DynamicFieldStub);
    fieldComponents[0].vm.$emit('change', { value: 'ds2', extra: { schema: 'other' } });
    await nextTick();

    const updated = wrapper.findAllComponents(DynamicFieldStub);
    expect(updated[0].props('modelValue')).toBe('ds2');
    expect(updated[1].props('modelValue')).toBe('other');
    expect(updated[2].props('modelValue')).toEqual([]);
  });

  it('同一数据源仅变更 Schema 时也清空下游表', async () => {
    const wrapper = createWrapper();
    await nextTick();

    const fieldComponents = wrapper.findAllComponents(DynamicFieldStub);
    fieldComponents[0].vm.$emit('change', { value: 'ds1', extra: { schema: 'other' } });
    await nextTick();

    const updated = wrapper.findAllComponents(DynamicFieldStub);
    expect(updated[0].props('modelValue')).toBe('ds1');
    expect(updated[1].props('modelValue')).toBe('other');
    expect(updated[2].props('modelValue')).toEqual([]);
  });
});
