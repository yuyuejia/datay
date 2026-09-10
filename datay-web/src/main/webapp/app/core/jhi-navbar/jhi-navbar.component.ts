import {
  type Ref,
  computed,
  defineComponent,
  inject,
  ref,
  watch,
  onMounted,
} from "vue";

import { useRouter } from "vue-router";
import { useLoginModal } from "@/account/login-modal";
import type AccountService from "@/account/account.service";
import EntitiesMenu from "@/entities/entities-menu.vue";

import { useStore } from "@/store";
import TenantSwitchService from "@/account/tenant-switch.service";

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: "JhiNavbar",
  components: {
    "entities-menu": EntitiesMenu,
  },
  setup() {
    const { showLogin } = useLoginModal();
    const accountService = inject<AccountService>("accountService");
    const currentLanguage = inject(
      "currentLanguage",
      () => computed(() => navigator.language ?? "zh-cn"),
      true,
    );

    const router = useRouter();
    const store = useStore();
    const tenantSwitchService = new TenantSwitchService();

    const version = `v${APP_VERSION}`;
    const hasAnyAuthorityValues: Ref<any> = ref({});

    const openAPIEnabled = computed(
      () => store.activeProfiles.indexOf("api-docs") > -1,
    );
    const inProduction = computed(
      () => store.activeProfiles.indexOf("prod") > -1,
    );
    const authenticated = computed(() => store.authenticated);

    const availableTenants: Ref<any[]> = ref([]);
    const currentTenantId: Ref<number | null> = ref(null);
    const currentTenantDisplay = computed(() => {
      const found = availableTenants.value.find(
        (t) => t.id === currentTenantId.value,
      );
      if (found) return found.name;
      if (availableTenants.value.length > 0)
        return availableTenants.value[0].name;
      return "租户";
    });

    const loadTenants = async () => {
      if (!authenticated.value) return;
      try {
        const res = await tenantSwitchService.getMyTenants();
        availableTenants.value = res.data || [];
        store.setAvailableTenants(availableTenants.value);

        const token =
          localStorage.getItem("jhi-authenticationToken") ||
          sessionStorage.getItem("jhi-authenticationToken");
        let jwtTenantId: number | null = null;
        if (token) {
          try {
            const payload = JSON.parse(atob(token.split(".")[1]));
            jwtTenantId = payload.tenantId ?? null;
          } catch {
            // ignore
          }
        }
        currentTenantId.value =
          jwtTenantId ?? availableTenants.value[0]?.id ?? null;
      } catch {
        availableTenants.value = [];
        store.setAvailableTenants([]);
      }
    };

    watch(
      authenticated,
      (val) => {
        if (val) {
          loadTenants();
        } else {
          availableTenants.value = [];
          currentTenantId.value = null;
        }
      },
      { immediate: true },
    );

    onMounted(() => {
      if (authenticated.value) {
        loadTenants();
      }
    });

    const subIsActive = (input: string | string[]) => {
      const paths = Array.isArray(input) ? input : [input];
      return paths.some((path) => {
        return router.currentRoute.value.path.indexOf(path) === 0;
      });
    };

    const isRememberMe = () =>
      !!localStorage.getItem("jhi-authenticationToken");

    const switchTenant = async (tenant: any) => {
      if (tenant.id === currentTenantId.value) return;
      try {
        const res = await tenantSwitchService.switchTenant(tenant.id);
        const jwt = res.headers.authorization?.slice(7);
        if (jwt) {
          const remember = isRememberMe();
          if (remember) {
            localStorage.setItem("jhi-authenticationToken", jwt);
            sessionStorage.removeItem("jhi-authenticationToken");
          } else {
            sessionStorage.setItem("jhi-authenticationToken", jwt);
            localStorage.removeItem("jhi-authenticationToken");
          }
          TenantSwitchService.cacheTenant(
            { id: tenant.id, code: tenant.code, name: tenant.name },
            remember,
          );
          currentTenantId.value = tenant.id;
          await accountService.retrieveAccount();
          store.incrementTenantVersion();
          router.replace({
            path: router.currentRoute.value.fullPath,
            force: true,
          });
        }
      } catch (err) {
        console.error("Failed to switch tenant", err);
      }
    };

    const logout = async () => {
      localStorage.removeItem("jhi-authenticationToken");
      sessionStorage.removeItem("jhi-authenticationToken");
      TenantSwitchService.clearCache();
      store.logout();
      router.push("/login");
    };

    return {
      logout,
      subIsActive,
      accountService,
      showLogin,
      version,
      currentLanguage,
      hasAnyAuthorityValues,
      openAPIEnabled,
      inProduction,
      authenticated,
      availableTenants,
      currentTenantId,
      currentTenantDisplay,
      switchTenant,
    };
  },
  methods: {
    hasAnyAuthority(authorities: any): boolean {
      this.accountService
        .hasAnyAuthorityAndCheckAuth(authorities)
        .then((value) => {
          if (this.hasAnyAuthorityValues[authorities] !== value) {
            this.hasAnyAuthorityValues = {
              ...this.hasAnyAuthorityValues,
              [authorities]: value,
            };
          }
        });
      return this.hasAnyAuthorityValues[authorities] ?? false;
    },
  },
});