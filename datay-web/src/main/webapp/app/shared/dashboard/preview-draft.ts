import type { DashboardSpec } from './types';

/** 浏览器本地暂存设计器预览定义的前缀。 */
export const DASHBOARD_PREVIEW_STORAGE_PREFIX = 'datay-dashboard-preview:';

/** 预览定义的保留时长，超时后由设计器或查看器清理。 */
export const DASHBOARD_PREVIEW_TTL_MS = 60 * 60 * 1000;

export interface DashboardPreviewDraft {
  name: string;
  spec: DashboardSpec;
  createdAt: number;
}

/**
 * 清理过期的预览定义，避免未保存看板长期占用浏览器存储。
 */
export function cleanupDashboardPreviewDrafts(now = Date.now()): void {
  try {
    for (let index = localStorage.length - 1; index >= 0; index -= 1) {
      const key = localStorage.key(index);
      if (!key?.startsWith(DASHBOARD_PREVIEW_STORAGE_PREFIX)) {
        continue;
      }
      const timestamp = Number(key.slice(DASHBOARD_PREVIEW_STORAGE_PREFIX.length).split('-')[0]);
      if (!Number.isFinite(timestamp) || now - timestamp > DASHBOARD_PREVIEW_TTL_MS) {
        localStorage.removeItem(key);
      }
    }
  } catch {
    // 浏览器禁用本地存储时忽略清理失败，由真正读写预览时提示。
  }
}

/**
 * 将当前设计暂存到本地，返回可在预览页地址中使用的草稿 id。
 */
export function createDashboardPreviewDraft(name: string, spec: DashboardSpec): string {
  cleanupDashboardPreviewDrafts();
  const id = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const draft: DashboardPreviewDraft = {
    name,
    spec: JSON.parse(JSON.stringify(spec)) as DashboardSpec,
    createdAt: Date.now(),
  };
  localStorage.setItem(`${DASHBOARD_PREVIEW_STORAGE_PREFIX}${id}`, JSON.stringify(draft));
  return id;
}

/**
 * 按草稿 id 读取设计器暂存的预览定义。
 */
export function readDashboardPreviewDraft(id: string): DashboardPreviewDraft | undefined {
  try {
    const raw = localStorage.getItem(`${DASHBOARD_PREVIEW_STORAGE_PREFIX}${id}`);
    if (!raw) {
      return undefined;
    }
    const draft = JSON.parse(raw) as Partial<DashboardPreviewDraft>;
    if (!draft?.spec || !Array.isArray(draft.spec.datasets) || !Array.isArray(draft.spec.widgets)) {
      return undefined;
    }
    return {
      name: draft.name || '',
      spec: draft.spec,
      createdAt: draft.createdAt ?? 0,
    };
  } catch {
    return undefined;
  }
}
