import { defineComponent, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';

import { useStore } from '@/store';
import { useTabStore, type TabItem } from '@/shared/config/store/tab-store';
import { HOME_TAB, HOME_TAB_NAME, TAB_DEFINITIONS, findTabDefinitionByPath } from './tab.config';

const toTabItem = (name: string, fullPath: string): TabItem | undefined => {
  const def = TAB_DEFINITIONS[name];
  if (!def) return undefined;
  return {
    name,
    path: def.path,
    fullPath,
    componentName: def.componentName,
    cachedComponents: [],
    title: def.title,
    closable: def.closable,
  };
};

export default defineComponent({
  name: 'TabView',
  setup() {
    const route = useRoute();
    const router = useRouter();
    const accountStore = useStore();
    const tabStore = useTabStore();
    const { tabs } = storeToRefs(tabStore);
    const { authenticated } = storeToRefs(accountStore);

    const activeName = ref('');

    const resolvedComponentName = (): string => {
      const record = route.matched[route.matched.length - 1];
      const component: any = record?.components?.default;
      const name = component?.name;
      return typeof name === 'string' ? name : (route.name as string);
    };

    const findOwner = (path: string): TabItem | undefined =>
      tabs.value
        .filter(tab => tab.name !== HOME_TAB_NAME && (path === tab.path || path.startsWith(`${tab.path}/`)))
        .sort((a, b) => b.path.length - a.path.length)[0];

    const ensureOwnerTab = (path: string): TabItem | undefined => {
      const existing = findOwner(path);
      if (existing) return existing;
      const found = findTabDefinitionByPath(path);
      if (!found) return undefined;
      tabStore.addTab({
        name: found.name,
        path: found.def.path,
        fullPath: route.fullPath,
        componentName: found.def.componentName,
        cachedComponents: [],
        title: found.def.title,
        closable: found.def.closable,
      });
      return tabs.value.find(tab => tab.name === found.name);
    };

    const syncActiveTab = () => {
      const routeName = route.name as string;
      const current = toTabItem(routeName, route.fullPath);
      if (current) {
        tabStore.addTab(current);
        activeName.value = routeName;
        return;
      }
      const componentName = resolvedComponentName();
      const owner = ensureOwnerTab(route.path);
      if (owner) {
        tabStore.cacheComponent(owner.name, componentName);
        if (routeName && routeName !== componentName) {
          tabStore.cacheComponent(owner.name, routeName);
        }
        tabStore.updateFullPath(owner.name, route.fullPath);
        activeName.value = owner.name;
      } else {
        tabStore.cacheView(componentName);
        if (routeName && routeName !== componentName) {
          tabStore.cacheView(routeName);
        }
      }
    };

    watch(() => route.fullPath, syncActiveTab, { immediate: true });

    watch(
      authenticated,
      value => {
        if (value) {
          tabStore.reset([{ ...HOME_TAB, name: HOME_TAB_NAME, fullPath: '/' }]);
          syncActiveTab();
        } else {
          tabStore.reset();
          activeName.value = '';
        }
      },
      { immediate: true },
    );

    const handleTabClick = (name: string) => {
      if (route.name === name) return;
      const tab = tabs.value.find(item => item.name === name);
      if (tab) {
        router.push(tab.fullPath || tab.path);
      }
    };

    const handleTabRemove = (name: string) => {
      const wasActive = activeName.value === name;
      const next = tabStore.removeTab(name);
      if (wasActive && next) {
        router.push(next.fullPath || next.path);
      }
    };

    const handleCommand = (command: string) => {
      if (command === 'others') {
        tabStore.removeOthers(activeName.value);
      } else if (command === 'all') {
        tabStore.removeAll();
        activeName.value = HOME_TAB_NAME;
        router.push('/');
      }
    };

    return {
      tabs,
      activeName,
      handleTabClick,
      handleTabRemove,
      handleCommand,
    };
  },
});
