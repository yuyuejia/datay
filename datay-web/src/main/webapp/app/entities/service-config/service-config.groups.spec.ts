import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  fileGet: vi.fn(),
  fileSave: vi.fn(),
  fileTest: vi.fn(),
  statusGet: vi.fn(),
  statusSave: vi.fn(),
  statusTest: vi.fn(),
  llmGet: vi.fn(),
  llmSave: vi.fn(),
  llmTest: vi.fn(),
  dsGet: vi.fn(),
  dsSave: vi.fn(),
}));

vi.mock('@/entities/file-management/file-management.service', () => ({
  default: class {
    getStorageConfig = mocks.fileGet;
    updateStorageConfig = mocks.fileSave;
    testStorageConfig = mocks.fileTest;
  },
}));

vi.mock('@/entities/etl-task/etl-task-state.service', () => ({
  default: class {
    getConfig = mocks.statusGet;
    updateConfig = mocks.statusSave;
    testConfig = mocks.statusTest;
  },
}));

vi.mock('./llm-config.service', () => ({
  default: class {
    getConfig = mocks.llmGet;
    updateConfig = mocks.llmSave;
    testConfig = mocks.llmTest;
  },
}));

vi.mock('@/entities/data-source/data-source.service', () => ({
  default: class {
    getDefaultWarehouse = mocks.dsGet;
    setDefaultWarehouse = mocks.dsSave;
  },
}));

import { SERVICE_CONFIG_GROUPS } from './service-config.groups';

describe('service-config groups', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('declares the expected groups in order', () => {
    expect(SERVICE_CONFIG_GROUPS.map(group => group.key)).toEqual(['data-warehouse', 'file-storage', 'status-storage', 'llm']);
  });

  it('delegates file storage load/save/test', async () => {
    const group = SERVICE_CONFIG_GROUPS[1];
    mocks.fileGet.mockResolvedValue({ type: 'local' });
    mocks.fileSave.mockResolvedValue(undefined);
    mocks.fileTest.mockResolvedValue({ success: true });

    await expect(group.load()).resolves.toEqual({ type: 'local' });
    await group.save({ type: 'local' });
    await group.test?.();

    expect(mocks.fileSave).toHaveBeenCalledWith({ type: 'local' });
    expect(mocks.fileTest).toHaveBeenCalled();
  });

  it('delegates status storage load/save/test', async () => {
    const group = SERVICE_CONFIG_GROUPS[2];
    mocks.statusGet.mockResolvedValue({ type: 'minio' });
    mocks.statusSave.mockResolvedValue(undefined);
    mocks.statusTest.mockResolvedValue({ success: true });

    await expect(group.load()).resolves.toEqual({ type: 'minio' });
    await group.save({ type: 'minio' });
    await group.test?.();

    expect(mocks.statusSave).toHaveBeenCalledWith({ type: 'minio' });
    expect(mocks.statusTest).toHaveBeenCalled();
  });

  it('delegates llm load/save/test', async () => {
    const group = SERVICE_CONFIG_GROUPS[3];
    mocks.llmGet.mockResolvedValue({ enabled: true, model: 'deepseek-chat' });
    mocks.llmSave.mockResolvedValue(undefined);
    mocks.llmTest.mockResolvedValue({ success: true });

    await expect(group.load()).resolves.toEqual({ enabled: true, model: 'deepseek-chat' });
    await group.save({ enabled: true, model: 'deepseek-chat' });
    await group.test?.();

    expect(mocks.llmSave).toHaveBeenCalledWith({ enabled: true, model: 'deepseek-chat' });
    expect(mocks.llmTest).toHaveBeenCalled();
  });

  it('loads and saves the default warehouse data source', async () => {
    const group = SERVICE_CONFIG_GROUPS[0];
    mocks.dsGet.mockResolvedValue({ data: { dataSourceId: 5 } });
    mocks.dsSave.mockResolvedValue(undefined);

    await expect(group.load()).resolves.toEqual({ dataSourceId: 5 });
    await group.save({ dataSourceId: 5 });

    expect(mocks.dsSave).toHaveBeenCalledWith(5);
    expect(group.test).toBeUndefined();
  });

  it('rejects saving a default warehouse without a selection', async () => {
    const group = SERVICE_CONFIG_GROUPS[0];
    await expect(group.save({ dataSourceId: null })).rejects.toThrow('请选择默认数仓数据源');
    expect(mocks.dsSave).not.toHaveBeenCalled();
  });

  it('toggles local/minio fields via showWhen', () => {
    const group = SERVICE_CONFIG_GROUPS[1];
    const localField = group.fields.find(field => field.key === 'localBasePath');
    const minioField = group.fields.find(field => field.key === 'minioEndpoint');

    expect(localField?.showWhen?.({ type: 'local' })).toBe(true);
    expect(localField?.showWhen?.({ type: 'minio' })).toBe(false);
    expect(minioField?.showWhen?.({ type: 'minio' })).toBe(true);
    expect(minioField?.showWhen?.({ type: 'local' })).toBe(false);
  });
});
