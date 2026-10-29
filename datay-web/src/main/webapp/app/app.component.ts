import { defineComponent, provide, computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute } from 'vue-router';

import Ribbon from '@/core/ribbon/ribbon.vue';
import JhiNavbar from '@/core/jhi-navbar/jhi-navbar.vue';
import TabView from '@/core/tab-view/tab-view.vue';
import { useAlertService } from '@/shared/alert/alert.service';
import { useStore } from '@/store';
import { useTabStore } from '@/shared/config/store/tab-store';
import { isEmbedMode } from '@/shared/config/embed';
import '@/shared/config/dayjs';

export default defineComponent({
  name: 'App',
  components: {
    ribbon: Ribbon,
    'jhi-navbar': JhiNavbar,
    'tab-view': TabView,
  },
  setup() {
    provide('alertService', useAlertService());

    const store = useStore();
    const { tenantVersion, authenticated, availableTenants, tenantsLoaded } = storeToRefs(store);
    const { cachedViews } = storeToRefs(useTabStore());

    const noTenant = computed(() => authenticated.value && tenantsLoaded.value && availableTenants.value.length === 0);

    const route = useRoute();

    const rootCachedViews = computed(() => ['Entities', ...cachedViews.value]);

    const rootKey = computed(() => {
      const top = route.matched[0];
      if (top?.name === 'Entities') {
        return `entities-${tenantVersion.value}`;
      }
      return `${route.fullPath}-${tenantVersion.value}`;
    });

    const isDesignPage = computed(() => {
      return (
        route.name === 'Home' ||
        route.name === 'ETLTaskDesign' ||
        route.name === 'ETLTaskDesignNew' ||
        route.name === 'ETLTaskCreate' ||
        route.name === 'DagJobDesign' ||
        route.name === 'DagJobDesignNew' ||
        route.name === 'DataSourceQuery' ||
        route.name === 'SqlJobCreate' ||
        route.name === 'SqlJobEdit' ||
        route.name === 'ShellJobCreate' ||
        route.name === 'ShellJobEdit' ||
        route.name === 'MetricAi' ||
        route.name === 'AnalysisDashboardDesign' ||
        route.name === 'AnalysisDashboardDesigner'
      );
    });

    const isListPage = computed(() => route.meta.listPage === true);

    /** 顶层路由自身就是列表页时（如系统管理），同样不使用 keep-alive，返回时重新挂载取数。 */
    const isTopLevelListPage = computed(() => route.matched[0]?.meta?.listPage === true);

    const isLoginPage = computed(() => route.name === 'Login');

    // 嵌入模式：隐藏顶栏（导航 + 菜单页签），让页面在 iframe 中铺满。
    const isEmbedded = isEmbedMode();

    // 把顶栏（导航 + 菜单页签）的实际高度写入 CSS 变量，供页面计算可视高度。
    const topbarRef = ref<HTMLElement | null>(null);
    let topbarObserver: ResizeObserver | undefined;

    const syncTopbarHeight = () => {
      const height = topbarRef.value?.getBoundingClientRect().height ?? 0;
      document.documentElement.style.setProperty('--app-topbar-height', `${height}px`);
    };

    const observeTopbar = () => {
      topbarObserver?.disconnect();
      topbarObserver = undefined;
      syncTopbarHeight();
      if (topbarRef.value && typeof ResizeObserver !== 'undefined') {
        topbarObserver = new ResizeObserver(syncTopbarHeight);
        topbarObserver.observe(topbarRef.value);
      }
    };

    onMounted(observeTopbar);
    watch(topbarRef, observeTopbar, { flush: 'post' });

    onBeforeUnmount(() => {
      topbarObserver?.disconnect();
    });

    return {
      isDesignPage,
      isListPage,
      isTopLevelListPage,
      isLoginPage,
      isEmbedded,
      topbarRef,
      tenantVersion,
      authenticated,
      route,
      noTenant,
      rootCachedViews,
      rootKey,
    };
  },
});
