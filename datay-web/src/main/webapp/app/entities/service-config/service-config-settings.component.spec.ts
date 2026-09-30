import { flushPromises, shallowMount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  axiosGet: vi.fn(),
  load: vi.fn(),
  save: vi.fn(),
  test: vi.fn(),
}));

vi.mock('axios', () => ({ default: { get: mocks.axiosGet } }));

vi.mock('./service-config.groups', () => ({
  SERVICE_CONFIG_GROUPS: [
    {
      key: 'file-storage',
      label: '文件存储',
      description: '文件存储配置',
      fields: [
        {
          key: 'type',
          label: '存储类型',
          type: 'select',
          options: [
            { label: '本地', value: 'local' },
            { label: 'MinIO', value: 'minio' },
          ],
        },
        { key: 'minioEndpoint', label: 'MinIO 地址', type: 'text', showWhen: (values: any) => values.type === 'minio' },
      ],
      load: mocks.load,
      save: mocks.save,
      test: mocks.test,
    },
    {
      key: 'data-warehouse',
      label: '默认数仓',
      description: '默认数仓配置',
      fields: [{ key: 'dataSourceId', label: '默认数仓', type: 'datasource' }],
      load: mocks.load,
      save: mocks.save,
    },
  ],
}));

import ServiceConfigSettings from './service-config-settings.vue';

const alertService = {
  showSuccess: vi.fn(),
  showError: vi.fn(),
  showWarning: vi.fn(),
  showHttpError: vi.fn(),
  showInfo: vi.fn(),
};

const mountComponent = () =>
  shallowMount(ServiceConfigSettings, {
    global: {
      stubs: {
        'font-awesome-icon': true,
        'router-link': true,
        'el-alert': true,
        'el-menu': true,
        'el-menu-item': true,
        'el-form': true,
        'el-form-item': true,
        'el-select': true,
        'el-option': true,
        'el-input': true,
        'el-button': true,
      },
      directives: { loading: {} },
      provide: { alertService },
    },
  });

describe('ServiceConfigSettings', () => {
  beforeEach(() => {
    mocks.load.mockReset().mockResolvedValue({ type: 'local' });
    mocks.save.mockReset().mockResolvedValue(undefined);
    mocks.test.mockReset().mockResolvedValue({ success: true, strategy: 'LocalFile' });
    mocks.axiosGet.mockReset().mockResolvedValue({ data: { tenantId: 1, tenantCode: 'acme', tenantName: 'Acme' } });
    alertService.showSuccess.mockReset();
    alertService.showError.mockReset();
  });

  it('loads the first group and the tenant context on mount', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    expect(mocks.load).toHaveBeenCalledTimes(1);
    expect(mocks.axiosGet).toHaveBeenCalledWith('api/tenant-settings/context');
    expect((wrapper.vm as any).tenantLabel).toBe('Acme（acme）');
  });

  it('reloads the active group when switching groups', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    (wrapper.vm as any).activeKey = 'data-warehouse';
    await flushPromises();

    expect(mocks.load).toHaveBeenCalledTimes(2);
    expect((wrapper.vm as any).activeGroup.key).toBe('data-warehouse');
  });

  it('hides fields whose showWhen is false', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    (wrapper.vm as any).values.type = 'local';
    await flushPromises();
    expect((wrapper.vm as any).visibleFields.map((field: any) => field.key)).toEqual(['type']);

    (wrapper.vm as any).values.type = 'minio';
    await flushPromises();
    expect((wrapper.vm as any).visibleFields.map((field: any) => field.key)).toEqual(['type', 'minioEndpoint']);
  });

  it('saves the active group and shows a success message', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    await (wrapper.vm as any).saveGroup();

    expect(mocks.save).toHaveBeenCalled();
    expect(alertService.showSuccess).toHaveBeenCalledWith('保存成功');
  });

  it('reports save failures', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    mocks.save.mockRejectedValue(new Error('boom'));
    await (wrapper.vm as any).saveGroup();

    expect(alertService.showError).toHaveBeenCalledWith('boom');
  });

  it('tests the active group connection', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    await (wrapper.vm as any).testGroup();
    expect(mocks.test).toHaveBeenCalled();
    expect(alertService.showSuccess).toHaveBeenCalledWith('测试连接成功（LocalFile）');

    mocks.test.mockResolvedValue({ success: false, error: 'nope' });
    await (wrapper.vm as any).testGroup();
    expect(alertService.showError).toHaveBeenCalledWith('nope');
  });

  it('skips testing when the group has no test handler', async () => {
    const wrapper = mountComponent();
    await flushPromises();

    (wrapper.vm as any).activeKey = 'data-warehouse';
    await flushPromises();

    await (wrapper.vm as any).testGroup();
    expect(mocks.test).not.toHaveBeenCalled();
  });
});
