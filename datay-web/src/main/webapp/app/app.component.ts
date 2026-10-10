import { defineComponent, provide, computed } from "vue";
import { storeToRefs } from "pinia";
import { useRoute } from "vue-router";

import Ribbon from "@/core/ribbon/ribbon.vue";
import JhiNavbar from "@/core/jhi-navbar/jhi-navbar.vue";
import TabView from "@/core/tab-view/tab-view.vue";
import { useAlertService } from "@/shared/alert/alert.service";
import { useStore } from "@/store";
import { useTabStore } from "@/shared/config/store/tab-store";
import "@/shared/config/dayjs";

export default defineComponent({
  name: "App",
  components: {
    ribbon: Ribbon,
    "jhi-navbar": JhiNavbar,
    "tab-view": TabView,
  },
  setup() {
    provide("alertService", useAlertService());

    const store = useStore();
    const { tenantVersion, authenticated, availableTenants, tenantsLoaded } =
      storeToRefs(store);
    const { cachedViews } = storeToRefs(useTabStore());

    const noTenant = computed(
      () =>
        authenticated.value &&
        tenantsLoaded.value &&
        availableTenants.value.length === 0,
    );

    const route = useRoute();

    const rootCachedViews = computed(() => ["Entities", ...cachedViews.value]);

    const rootKey = computed(() => {
      const top = route.matched[0];
      if (top?.name === "Entities") {
        return `entities-${tenantVersion.value}`;
      }
      return `${route.fullPath}-${tenantVersion.value}`;
    });

    const isDesignPage = computed(() => {
      return (
        route.name === "Home" ||
        route.name === "ETLTaskDesign" ||
        route.name === "ETLTaskDesignNew" ||
        route.name === "ETLTaskCreate" ||
        route.name === "DagJobDesign" ||
        route.name === "DagJobDesignNew" ||
        route.name === "DataSourceQuery" ||
        route.name === "AnalysisDashboardDesign"
      );
    });

    const isLoginPage = computed(() => route.name === "Login");

    return {
      isDesignPage,
      isLoginPage,
      tenantVersion,
      authenticated,
      route,
      noTenant,
      rootCachedViews,
      rootKey,
    };
  },
});
