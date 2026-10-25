import {
  DASHBOARD_PREVIEW_STORAGE_PREFIX,
  cleanupDashboardPreviewDrafts,
  createDashboardPreviewDraft,
  readDashboardPreviewDraft,
} from './preview-draft';
import type { DashboardSpec } from './types';

const previewSpec: DashboardSpec = {
  version: 1,
  title: '预览测试',
  datasets: [{ id: 'ds1', type: 'SQL' }],
  widgets: [
    {
      id: 'widget1',
      title: '图表',
      type: 'echarts',
      layout: { x: 0, y: 0, w: 6, h: 4 },
    },
  ],
  filters: [],
};

describe('dashboard preview draft', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('创建并读取预览定义', () => {
    const id = createDashboardPreviewDraft('销售看板', previewSpec);
    const draft = readDashboardPreviewDraft(id);

    expect(draft).toBeDefined();
    expect(draft?.name).toBe('销售看板');
    expect(draft?.spec.title).toBe('预览测试');
    expect(draft?.spec.widgets).toHaveLength(1);
  });

  it('读取不存在或损坏的预览定义返回空', () => {
    expect(readDashboardPreviewDraft('missing')).toBeUndefined();

    const key = `${DASHBOARD_PREVIEW_STORAGE_PREFIX}broken`;
    localStorage.setItem(key, '{not-json');
    expect(readDashboardPreviewDraft('broken')).toBeUndefined();
  });

  it('清理超过保留时长的预览定义', () => {
    const now = Date.now();
    const expiredKey = `${DASHBOARD_PREVIEW_STORAGE_PREFIX}${now - 2 * 60 * 60 * 1000}-old`;
    const activeKey = `${DASHBOARD_PREVIEW_STORAGE_PREFIX}${now}-active`;
    localStorage.setItem(expiredKey, JSON.stringify({ name: '旧预览', spec: previewSpec, createdAt: now }));
    localStorage.setItem(activeKey, JSON.stringify({ name: '新预览', spec: previewSpec, createdAt: now }));

    cleanupDashboardPreviewDrafts(now);

    expect(localStorage.getItem(expiredKey)).toBeNull();
    expect(localStorage.getItem(activeKey)).not.toBeNull();
  });
});
