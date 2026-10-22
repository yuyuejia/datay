import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, nextTick, ref } from 'vue';

const listMock = vi.hoisted(() => vi.fn());

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/shared/dataset/dataset.service', () => ({
  default: vi.fn().mockImplementation(() => ({
    list: listMock,
    preview: vi.fn(),
    remove: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    validate: vi.fn(),
  })),
}));

import AnalysisDatasetList from './analysis-dataset.vue';

const Host = defineComponent({
  components: { AnalysisDatasetList },
  setup() {
    const visible = ref(true);
    return { visible };
  },
  template: '<KeepAlive><AnalysisDatasetList v-if="visible" /></KeepAlive>',
});

describe('AnalysisDataset list', () => {
  beforeEach(() => {
    listMock.mockReset();
    listMock.mockResolvedValue([]);
  });

  it('从编辑页返回列表时重新取数，避免 keep-alive 缓存旧列表', async () => {
    const wrapper = mount(Host, {
      global: {
        provide: {
          alertService: { showHttpError: vi.fn() },
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
          'app-modal': true,
        },
      },
    });

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
});
