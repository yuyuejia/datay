import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import DashboardViewer from './App.vue';

const dashboardSpec = {
  version: 2,
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
  filters: [
    {
      id: 'region',
      type: 'select',
      targets: [{ datasetId: 'ds1', field: 'region', mode: 'server' }],
    },
  ],
};

const standardSpec = {
  version: 2,
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

const defaultedSpec = {
  ...standardSpec,
  title: '默认值看板',
  filters: [
    {
      id: 'date',
      type: 'dateRange',
      targets: [{ datasetId: 'ds1', field: 'stat_date', mode: 'server' }],
      defaultValue: { start: '2026-01-01', end: '2026-01-31' },
    },
  ],
};

const { loadAllMock } = vi.hoisted(() => ({ loadAllMock: vi.fn() }));

vi.mock('@/shared/dashboard/dashboard.service', () => ({
  default: class {
    static parseSpec = (dashboard: any) => (typeof dashboard.spec === 'string' ? JSON.parse(dashboard.spec) : dashboard.spec);
    get = vi.fn().mockImplementation(() =>
      Promise.resolve({
        id: 'db1',
        name: '看板',
        spec: JSON.stringify(
          window.location.search.includes('defaults')
            ? defaultedSpec
            : window.location.search.includes('standard')
              ? standardSpec
              : window.location.search.includes('nofilters')
                ? { ...dashboardSpec, filters: [] }
                : dashboardSpec,
        ),
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
      widgetData: ref({}),
      loadingDatasets: ref({}),
      datasetErrors: ref({}),
      widgetErrors: ref({}),
      serverPaginationWidgets: ref({}),
      dataVersion: ref(0),
      loading: ref(false),
      loadAll: loadAllMock,
      loadWidgetPage: vi.fn().mockResolvedValue(undefined),
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

const DashboardFiltersStub = defineComponent({
  name: 'DashboardFilters',
  props: {
    manualQuery: { type: Boolean, default: false },
    modelValue: { type: Object, default: () => ({}) },
  },
  emits: ['search', 'update:modelValue'],
  template: '<div class="dashboard-filters-stub"></div>',
});

describe('dashboard viewer screen settings', () => {
  beforeEach(() => {
    sessionStorage.clear();
    loadAllMock.mockClear();
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

  it('collapses screen filters by default and toggles them on click', async () => {
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

    expect(wrapper.find('.dashboard-viewer-filters-toggle').exists()).toBe(true);
    expect(wrapper.find('.dashboard-viewer-filters-toggle').text()).toBe('');
    expect(wrapper.find('.dashboard-viewer-filters-float').exists()).toBe(false);

    await wrapper.find('.dashboard-viewer-filters-toggle').trigger('click');
    expect(wrapper.find('.dashboard-viewer-filters-float').exists()).toBe(true);

    await wrapper.find('.dashboard-viewer-filters-toggle').trigger('click');
    expect(wrapper.find('.dashboard-viewer-filters-float').exists()).toBe(false);

    wrapper.unmount();
  });

  it('applies screen filters only after the user triggers search', async () => {
    const wrapper = mount(DashboardViewer, {
      global: {
        stubs: {
          DashboardStage: DashboardStageStub,
          DashboardCanvas: true,
          DashboardFilters: DashboardFiltersStub,
        },
      },
    });
    await flushPromises();
    loadAllMock.mockClear();

    await wrapper.find('.dashboard-viewer-filters-toggle').trigger('click');
    const filters = wrapper.findComponent(DashboardFiltersStub);
    expect(filters.exists()).toBe(true);
    expect(filters.props('manualQuery')).toBe(true);

    filters.vm.$emit('update:modelValue', { region: '华北' });
    await flushPromises();
    expect(loadAllMock).not.toHaveBeenCalled();

    filters.vm.$emit('search', { region: '华东' });
    await flushPromises();
    expect(loadAllMock).toHaveBeenCalledTimes(1);
    expect(loadAllMock).toHaveBeenCalledWith({ region: '华东' }, { force: true });

    wrapper.unmount();
  });

  it('applies filter defaults on the initial query and passes them to the filter bar', async () => {
    window.history.pushState({}, '', '/dashboard.html?id=defaults');
    const wrapper = mount(DashboardViewer, {
      global: {
        stubs: {
          DashboardStage: DashboardStageStub,
          DashboardCanvas: true,
          DashboardFilters: DashboardFiltersStub,
        },
      },
    });
    await flushPromises();

    const expected = { date: { start: '2026-01-01', end: '2026-01-31' } };
    expect(loadAllMock).toHaveBeenCalledWith(expected, { force: true });
    expect(wrapper.findComponent(DashboardFiltersStub).props('modelValue')).toEqual(expected);

    wrapper.unmount();
  });

  it('hides the screen filter toggle when no filters are configured', async () => {
    window.history.pushState({}, '', '/dashboard.html?id=nofilters');
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

    expect(wrapper.find('.dashboard-viewer-filters-toggle').exists()).toBe(false);
    expect(wrapper.find('.dashboard-viewer-filters-float').exists()).toBe(false);

    wrapper.unmount();
  });

  it('hides the topbar in embed mode', async () => {
    window.history.pushState({}, '', '/dashboard.html?id=standard&embed=1');
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

    expect(wrapper.classes()).toContain('is-embed');
    expect(wrapper.find('.dashboard-viewer-topbar').exists()).toBe(false);

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
