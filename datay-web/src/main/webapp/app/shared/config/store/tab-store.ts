import { defineStore } from 'pinia';

export interface TabItem {
  /** Route name, unique identity of the tab. */
  name: string;
  /** Canonical menu path of the tab, used for prefix ownership. */
  path: string;
  /** Last visited full path inside the tab, used when clicking the tab. */
  fullPath: string;
  /** Resolved component name, used by keep-alive `include`. */
  componentName: string;
  /** Resolved component names of nested pages visited inside this tab. */
  cachedComponents: string[];
  /** Display title shown on the tab. */
  title: string;
  /** Whether the tab can be closed. */
  closable: boolean;
}

interface TabState {
  tabs: TabItem[];
  /** Cached component names of pages that have no owning tab. */
  extraCachedViews: string[];
}

export const useTabStore = defineStore('tabs', {
  state: (): TabState => ({
    tabs: [],
    extraCachedViews: [],
  }),
  getters: {
    cachedViews: (state): string[] => {
      const names = new Set<string>();
      state.tabs.forEach(tab => {
        if (tab.componentName) {
          names.add(tab.componentName);
        }
        tab.cachedComponents.forEach(name => names.add(name));
      });
      state.extraCachedViews.forEach(name => names.add(name));
      return [...names];
    },
    tabCount: (state): number => state.tabs.length,
  },
  actions: {
    addTab(tab: TabItem) {
      const existing = this.tabs.find(item => item.name === tab.name);
      if (existing) {
        existing.fullPath = tab.fullPath;
        return;
      }
      this.tabs.push({ cachedComponents: [], ...tab });
    },
    cacheComponent(tabName: string, componentName: string) {
      const tab = this.tabs.find(item => item.name === tabName);
      if (tab && componentName && !tab.cachedComponents.includes(componentName)) {
        tab.cachedComponents.push(componentName);
      }
    },
    cacheView(componentName: string) {
      if (componentName && !this.extraCachedViews.includes(componentName)) {
        this.extraCachedViews.push(componentName);
      }
    },
    updateFullPath(name: string, fullPath: string) {
      const tab = this.tabs.find(item => item.name === name);
      if (tab) {
        tab.fullPath = fullPath;
      }
    },
    removeTab(name: string): TabItem | undefined {
      const index = this.tabs.findIndex(item => item.name === name);
      if (index === -1) {
        return undefined;
      }
      this.tabs.splice(index, 1);
      return this.tabs[index] ?? this.tabs[index - 1] ?? this.tabs[0];
    },
    removeOthers(name: string) {
      this.tabs = this.tabs.filter(tab => tab.name === name || !tab.closable);
    },
    removeAll() {
      this.tabs = this.tabs.filter(tab => !tab.closable);
    },
    reset(seed: TabItem[] = []) {
      this.tabs = seed.map(tab => ({ cachedComponents: [], ...tab }));
      this.extraCachedViews = [];
    },
  },
});
