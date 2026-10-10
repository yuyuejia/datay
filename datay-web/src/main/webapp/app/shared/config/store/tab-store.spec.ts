import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it } from 'vitest';

import { useTabStore, type TabItem } from './tab-store';

const tab = (overrides: Partial<TabItem> = {}): TabItem => ({
  name: 'DataSource',
  path: '/data-source',
  fullPath: '/data-source',
  componentName: 'DataSource',
  cachedComponents: [],
  title: '数据源管理',
  closable: true,
  ...overrides,
});

const home = tab({
  name: 'Home',
  path: '/',
  fullPath: '/',
  componentName: 'Home',
  title: '首页',
  closable: false,
});

describe('Tab Store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('should add a tab', () => {
    const store = useTabStore();
    store.addTab(home);
    store.addTab(tab());
    expect(store.tabs.map(item => item.name)).toEqual(['Home', 'DataSource']);
  });

  it('should not duplicate a tab but refresh its fullPath', () => {
    const store = useTabStore();
    store.addTab(tab());
    store.addTab(tab({ fullPath: '/data-source/1/edit' }));
    expect(store.tabs).toHaveLength(1);
    expect(store.tabs[0].fullPath).toBe('/data-source/1/edit');
  });

  it('should expose cached view component names', () => {
    const store = useTabStore();
    store.addTab(home);
    store.addTab(tab());
    expect(store.cachedViews).toEqual(['Home', 'DataSource']);
  });

  it('should cache nested components of a tab and evict them on close', () => {
    const store = useTabStore();
    store.addTab(tab());
    store.cacheComponent('DataSource', 'DataSourceQuery');
    expect(store.cachedViews).toEqual(['DataSource', 'DataSourceQuery']);

    store.removeTab('DataSource');
    expect(store.cachedViews).toEqual([]);
  });

  it('should cache pages without an owning tab and clear them on reset', () => {
    const store = useTabStore();
    store.cacheView('OrphanPage');
    expect(store.cachedViews).toContain('OrphanPage');

    store.reset();
    expect(store.cachedViews).toEqual([]);
  });

  it('should return a neighbouring tab when removing', () => {
    const store = useTabStore();
    store.addTab(home);
    store.addTab(tab());
    store.addTab(tab({ name: 'ETLTask', path: '/etl-task', componentName: 'ETLTask' }));

    const next = store.removeTab('DataSource');
    expect(next?.name).toBe('ETLTask');
    expect(store.tabs.map(item => item.name)).toEqual(['Home', 'ETLTask']);
  });

  it('should keep the active tab and non-closable tabs on removeOthers', () => {
    const store = useTabStore();
    store.addTab(home);
    store.addTab(tab());
    store.addTab(tab({ name: 'ETLTask', path: '/etl-task', componentName: 'ETLTask' }));

    store.removeOthers('ETLTask');
    expect(store.tabs.map(item => item.name)).toEqual(['Home', 'ETLTask']);
  });

  it('should only keep non-closable tabs on removeAll', () => {
    const store = useTabStore();
    store.addTab(home);
    store.addTab(tab());
    store.removeAll();
    expect(store.tabs.map(item => item.name)).toEqual(['Home']);
  });

  it('should reset to the provided seed', () => {
    const store = useTabStore();
    store.addTab(tab());
    store.reset([home]);
    expect(store.tabs).toEqual([home]);
  });
});
