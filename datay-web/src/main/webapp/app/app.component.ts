import { defineComponent, provide, computed } from "vue";
import { storeToRefs } from "pinia";
import { useRoute } from "vue-router";

import Ribbon from "@/core/ribbon/ribbon.vue";
import JhiNavbar from "@/core/jhi-navbar/jhi-navbar.vue";
import { useAlertService } from "@/shared/alert/alert.service";
import { useStore } from "@/store";
import "@/shared/config/dayjs";

export default defineComponent({
  name: "App",
  components: {
    ribbon: Ribbon,
    "jhi-navbar": JhiNavbar,
  },
  setup() {
    provide("alertService", useAlertService());

    const store = useStore();
    const { tenantVersion, authenticated, availableTenants, tenantsLoaded } =
      storeToRefs(store);

    const noTenant = computed(
      () =>
        authenticated.value &&
        tenantsLoaded.value &&
        availableTenants.value.length === 0,
    );

    const route = useRoute();
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
      route,
      noTenant,
    };
  },
});
