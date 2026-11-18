import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import DashboardFilters from './DashboardFilters.vue';
import type { DashboardFilter, FilterValues } from './types';

const inputFilter: DashboardFilter = { id: 'keyword', type: 'input', label: '关键字' };

const mountFilters = (options: { manualQuery?: boolean; filters?: DashboardFilter[]; modelValue?: FilterValues } = {}) =>
  mount(DashboardFilters, {
    props: {
      filters: options.filters ?? [inputFilter],
      modelValue: options.modelValue ?? { keyword: '' },
      manualQuery: options.manualQuery,
    },
  });

const findButton = (wrapper: ReturnType<typeof mountFilters>, label: string) =>
  wrapper.findAll('button').find(button => button.text() === label);

describe('DashboardFilters manual query', () => {
  it('只更新草稿值，点击查询后才触发 search', async () => {
    const wrapper = mountFilters({ manualQuery: true });
    const input = wrapper.find('input');

    await input.setValue('华东');

    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    expect(wrapper.emitted('search')).toBeUndefined();

    await findButton(wrapper, '查询')!.trigger('click');

    expect(wrapper.emitted('search')).toEqual([[{ keyword: '华东' }]]);
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();

    wrapper.unmount();
  });

  it('输入框回车等价点击查询', async () => {
    const wrapper = mountFilters({ manualQuery: true });
    const input = wrapper.find('input');

    await input.setValue('华南');
    await input.trigger('keyup.enter');

    expect(wrapper.emitted('search')).toEqual([[{ keyword: '华南' }]]);

    wrapper.unmount();
  });

  it('重置会恢复默认值并立即查询', async () => {
    const wrapper = mountFilters({
      manualQuery: true,
      filters: [{ ...inputFilter, defaultValue: '全部' }],
      modelValue: { keyword: '全部' },
    });

    await wrapper.find('input').setValue('华东');
    expect(wrapper.emitted('search')).toBeUndefined();

    await findButton(wrapper, '重置')!.trigger('click');

    expect(wrapper.emitted('search')).toEqual([[{ keyword: '全部' }]]);

    wrapper.unmount();
  });

  it('非手动模式下保持即时更新，且不显示查询按钮', async () => {
    const wrapper = mountFilters();
    const input = wrapper.find('input');

    await input.setValue('华北');

    expect(wrapper.emitted('update:modelValue')).toEqual([[{ keyword: '华北' }]]);
    expect(wrapper.emitted('search')).toBeUndefined();
    expect(findButton(wrapper, '查询')).toBeUndefined();

    wrapper.unmount();
  });
});
