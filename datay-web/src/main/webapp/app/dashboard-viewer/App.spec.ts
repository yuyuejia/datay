import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import DashboardViewer from './App.vue';

const dashboardSpec = {
  title: '大屏看板',
  layout: {
    mode: 'screen',
    columns: 24,
    rowHeight: 40,
    gap: 12,
    screen: {
      width: 1920,
      height: 1080,
      fitMode: 'stretch',
      theme: 'dark',
      autoRefreshSeconds: 30,
      background: { type: 'color', color: '#123456' },
    },
  },
  datasets: [{ id: 'ds1', type: 'SQL' }],
  widgets: [],
  filters: [],
};

const standardSpec = {
  title: '标准看板',
  layout: {
    mode: 'standard',
    columns: 24,
    rowHeight: 40,
    gap: 12,
    screen: {
      width: 1920,
      height: 1080,
      fitMode: 'stretch',
      theme: 'dark',
      autoRefreshSeconds: 30,
      background: { type: 'color', color: '#654321' },
    },
  },
  datasets: [{ id: 'ds1', type: 'SQL' }],
  widgets: [],
  filters: [],
};

vi.mock('@/shared/dashboard/dashboard.service', () => ({
  default: class {
    static parseSpec = (dashboard: any) => (typeof dashboard.spec === 'string' ? JSON.parse(dashboard.spec) : dashboard.spec);
    get = vi.fn().mockImplementation(() =>
      Promise.resolve({
        id: 'db1',
        name: '看板',
        spec: JSON.stringify(window.location.search.includes('standard') ? standardSpec : dashboardSpec),
      }),
    );
    getByCode = vi.fn();
  },
}));

vi.mock('@/shared/dashboard/useDashboardData', async () => {
  const { ref } = await import('vue');
  return {
    useDashboardData: () => ({
      datasetData: ref({}),
      rawDatasetData: ref({}),
      loadingDatasets: ref({}),
      datasetErrors: ref({}),
      loading: ref(false),
      loadAll: vi.fn().mockResolvedValue(undefined),
    }),
  };
});

const DashboardStageStub = defineComponent({
  name: 'DashboardStage',
  props: {
    width: { type: Number, default: 1920 },
    height: { type: Number, default: 1080 },
    zoom: { type: [Number, String], default: 'fit' },
    fitMode: { type: String, default: 'contain' },
    padding: { type: Number, default: 0 },
    backgroundStyle: { type: Object, default: () => ({}) },
  },
  template: '<div class="stage-stub"></div>',
});

describe('dashboard viewer screen settings', () => {
  beforeEach(() => {
    window.history.pushState({}, '', '/dashboard.html?id=db1');
  });

  it('passes fit mode, theme and background to the stage', async () => {
    const wrapper = mount(DashboardViewer, {
      global: {
        stubs: {
          DashboardStage: DashboardStageStub,
          DashboardCanvas: true,
          DashboardFilters: true,
        },
      },
    });
    await flushPromises();

    const stage = wrapper.findComponent(DashboardStageStub);
    expect(stage.exists()).toBe(true);
    expect(stage.props('fitMode')).toBe('stretch');
    expect(stage.props('backgroundStyle')).toEqual({ backgroundColor: 'transparent' });
    expect(wrapper.element.style.backgroundColor).not.toBe('');
    expect(wrapper.classes()).toContain('dashboard-theme-dark');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    wrapper.unmount();
  });

  it('applies theme and background in standard mode', async () => {
    window.history.pushState({}, '', '/dashboard.html?id=standard');
    const wrapper = mount(DashboardViewer, {
      global: {
        stubs: {
          DashboardStage: DashboardStageStub,
          DashboardCanvas: true,
          DashboardFilters: true,
        },
      },
    });
    await flushPromises();

    expect(wrapper.findComponent(DashboardStageStub).exists()).toBe(false);
    expect(wrapper.classes()).toContain('dashboard-theme-dark');
    expect(wrapper.element.style.backgroundColor).not.toBe('');
    expect(document.documentElement.classList.contains('dark')).toBe(true);

    wrapper.unmount();
  });
});
