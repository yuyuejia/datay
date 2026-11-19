import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick, ref } from 'vue';

const listMock = vi.hoisted(() => vi.fn());
const getMock = vi.hoisted(() => vi.fn());
const updateMock = vi.hoisted(() => vi.fn());
const persistStatusMock = vi.hoisted(() => vi.fn());
const runPersistMock = vi.hoisted(() => vi.fn());
const syncPersistMock = vi.hoisted(() => vi.fn());

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/shared/dataset/dataset.service', () => {
  const DatasetServiceMock = vi.fn().mockImplementation(() => ({
    list: listMock,
    get: getMock,
    preview: vi.fn(),
    remove: vi.fn(),
    create: vi.fn(),
    update: updateMock,
    validate: vi.fn(),
    persistStatus: persistStatusMock,
    runPersist: runPersistMock,
    syncPersist: syncPersistMock,
  }));
  (DatasetServiceMock as any).parseDefinition = (dataset?: { definition?: string | Record<string, any> }) => {
    if (!dataset?.definition) {
      return {};
    }
    return typeof dataset.definition === 'string' ? JSON.parse(dataset.definition) : dataset.definition;
  };
  return { default: DatasetServiceMock };
});

import AnalysisDatasetList from './analysis-dataset.vue';

const Host = defineComponent({
  components: { AnalysisDatasetList },
  setup() {
    const visible = ref(true);
    return { visible };
  },
  template: '<KeepAlive><AnalysisDatasetList v-if="visible" /></KeepAlive>',
});

const mountList = () =>
  mount(Host, {
    global: {
      provide: {
        alertService: { showHttpError: vi.fn(), showError: vi.fn() },
      },
      stubs: {
        'font-awesome-icon': true,
        'el-table': true,
        'el-table-column': true,
        'el-dialog': true,
        'el-button': true,
        'el-input': true,
        'el-select': true,
        'el-option': true,
        'el-tag': true,
        'el-empty': true,
        'el-switch': true,
        'el-form': true,
        'el-form-item': true,
        'el-radio-group': true,
        'el-radio-button': true,
        'el-alert': true,
        'el-dropdown': true,
        'el-dropdown-menu': true,
        'el-dropdown-item': true,
        CronExpressionSelector: true,
        DataSourceSelector: true,
        'app-modal': true,
      },
    },
  });

describe('AnalysisDataset list', () => {
  beforeEach(() => {
    listMock.mockReset();
    listMock.mockResolvedValue([]);
    getMock.mockReset();
    updateMock.mockReset();
  });

  it('从编辑页返回列表时重新取数，避免 keep-alive 缓存旧列表', async () => {
    const wrapper = mountList();

    await flushPromises();
    expect(listMock).toHaveBeenCalledTimes(1);

    (wrapper.vm as any).visible = false;
    await nextTick();
    (wrapper.vm as any).visible = true;
    await nextTick();
    await flushPromises();

    expect(listMock).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it('在列表页弹框中保存持久化配置', async () => {
    const row = {
      id: 'ds1',
      name: '订单数据集',
      code: 'orders',
      type: 'SQL' as const,
      definition: JSON.stringify({ sql: 'SELECT 1' }),
      persistEnabled: false,
    };
    getMock.mockResolvedValue(row);
    updateMock.mockImplementation((id: string, dto: Record<string, any>) => ({ ...dto, id }));
    const wrapper = mountList();
    await flushPromises();
    const list = wrapper.findComponent(AnalysisDatasetList);

    await (list.vm as any).openPersist(row);
    await flushPromises();
    expect((list.vm as any).persistVisible).toBe(true);

    Object.assign((list.vm as any).persistForm, {
      persistEnabled: true,
      persistTargetDataSourceId: 'db1',
      persistTargetTable: 'orders_result',
      persistWriteMode: 'REPLACE',
      persistCron: '',
    });
    await (list.vm as any).savePersist();
    await flushPromises();

    expect(updateMock).toHaveBeenCalledWith(
      'ds1',
      expect.objectContaining({
        persistEnabled: true,
        persistTargetDataSourceId: 'db1',
        persistTargetTable: 'orders_result',
        persistWriteMode: 'REPLACE',
      }),
    );
    wrapper.unmount();
  });
});
