import { flushPromises, shallowMount } from '@vue/test-utils';
import sinon, { type SinonStubbedInstance } from 'sinon';
import { nextTick } from 'vue';
import { vitest } from 'vitest';

import DataApiStats from './data-api-stats.vue';
import DataApiService from './data-api.service';
import AlertService from '@/shared/alert/alert.service';

vitest.mock('vue-router', () => ({
  useRoute: () => ({ params: { dataApiId: 'api-1' } }),
  useRouter: () => ({ push: vitest.fn() }),
}));

const bModalStub = {
  render: () => {},
  methods: {
    hide: () => {},
    show: () => {},
  },
};

describe('DataApiStats', () => {
  let dataApiServiceStub: SinonStubbedInstance<DataApiService>;
  let alertService: AlertService;

  beforeEach(() => {
    dataApiServiceStub = sinon.createStubInstance<DataApiService>(DataApiService);
    dataApiServiceStub.retrieveCallStats.resolves({
      apiId: 'api-1',
      apiCode: 'orders_api',
      apiName: '订单查询服务',
      totalCount: 10,
      successCount: 9,
      failureCount: 1,
      successRate: 90,
      avgDurationMs: 12.5,
      maxDurationMs: 30,
      trend: [],
    });
    dataApiServiceStub.retrieveCallLogs.resolves({
      headers: { 'x-total-count': '1' },
      data: [{ id: 'log-1', status: 'SUCCESS', durationMs: 12 }],
    });
    alertService = new AlertService();
  });

  it('should load stats and logs on mount', async () => {
    const wrapper = shallowMount(DataApiStats, {
      global: {
        stubs: {
          AppModal: bModalStub as any,
          EChart: true,
          'font-awesome-icon': true,
          'router-link': true,
          'el-table': true,
          'el-table-column': true,
          'el-tag': true,
          'el-button': true,
          'el-button-group': true,
          'el-select': true,
          'el-option': true,
          'el-date-picker': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'jhi-item-count': true,
          'el-pagination': true,
        },
        provide: {
          alertService,
          dataApiService: () => dataApiServiceStub,
        },
      },
    });
    await flushPromises();
    await nextTick();

    expect(dataApiServiceStub.retrieveCallStats.calledOnce).toBeTruthy();
    expect(dataApiServiceStub.retrieveCallLogs.calledOnce).toBeTruthy();
    expect(dataApiServiceStub.retrieveCallLogs.lastCall.args[1]).not.toHaveProperty('status');
    expect(wrapper.vm.stats.totalCount).toBe(10);
    expect(wrapper.vm.logs).toHaveLength(1);
    expect(wrapper.vm.formatDuration(123)).toBe('123 ms');
  });

  it('should pass status filter when switching status', async () => {
    const wrapper = shallowMount(DataApiStats, {
      global: {
        stubs: {
          AppModal: bModalStub as any,
          EChart: true,
          'font-awesome-icon': true,
          'router-link': true,
          'el-table': true,
          'el-table-column': true,
          'el-tag': true,
          'el-button': true,
          'el-button-group': true,
          'el-select': true,
          'el-option': true,
          'el-date-picker': true,
          'el-descriptions': true,
          'el-descriptions-item': true,
          'jhi-item-count': true,
          'el-pagination': true,
        },
        provide: {
          alertService,
          dataApiService: () => dataApiServiceStub,
        },
      },
    });
    await flushPromises();

    wrapper.vm.statusFilter = 'SUCCESS';
    await wrapper.vm.handleStatusChange();
    await flushPromises();

    expect(dataApiServiceStub.retrieveCallLogs.lastCall.args[1]).toMatchObject({ status: 'SUCCESS' });
  });
});
