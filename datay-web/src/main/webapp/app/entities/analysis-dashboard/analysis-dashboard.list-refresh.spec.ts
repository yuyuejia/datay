import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AnalysisDashboard from './analysis-dashboard.vue';

const { listMock } = vi.hoisted(() => ({
  listMock: vi.fn(),
}));

vi.mock('@/shared/dashboard/dashboard.service', () => ({
  default: class {
    list = listMock;
  },
}));

const Designer = defineComponent({
  name: 'AnalysisDashboardDesigner',
  template: '<div class="designer-stub">designer</div>',
});

const EntitiesHost = defineComponent({
  name: 'Entities',
  template: `
    <router-view v-slot="{ Component }">
      <keep-alive :include="['AnalysisDashboardDesigner']">
        <component v-if="!$route.meta.listPage" :is="Component" :key="$route.fullPath" />
      </keep-alive>
      <component v-if="$route.meta.listPage" :is="Component" :key="$route.fullPath" />
    </router-view>
  `,
});

const AppHost = defineComponent({
  template: `
    <router-view v-slot="{ Component }">
      <keep-alive include="Entities">
        <component :is="Component" />
      </keep-alive>
    </router-view>
  `,
});

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/',
        name: 'Entities',
        component: EntitiesHost,
        children: [
          { path: 'analysis-dashboard', name: 'AnalysisDashboard', component: AnalysisDashboard, meta: { listPage: true } },
          { path: 'analysis-dashboard/designer', name: 'AnalysisDashboardDesigner', component: Designer },
        ],
      },
    ],
  });
}

describe('AnalysisDashboard tab refresh', () => {
  beforeEach(() => {
    listMock.mockReset();
    listMock.mockResolvedValue([]);
  });

  it('refreshes when returning to the list route', async () => {
    const router = createTestRouter();
    router.push('/analysis-dashboard');
    await router.isReady();

    const wrapper = mount(AppHost, {
      global: {
        plugins: [router],
        provide: { alertService: { showHttpError: vi.fn() } },
        stubs: {
          'font-awesome-icon': true,
          'app-modal': true,
        },
      },
    });
    await flushPromises();
    expect(listMock).toHaveBeenCalledTimes(1);

    await router.push('/analysis-dashboard/designer');
    await flushPromises();

    await router.push('/analysis-dashboard');
    await flushPromises();

    expect(listMock).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });
});
