import axios from 'axios';
import { type Ref, defineComponent, inject, ref } from 'vue';
import { useRouter } from 'vue-router';
import type AccountService from '../account.service';
import TenantSwitchService from '@/account/tenant-switch.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  setup() {
    const authenticationError: Ref<boolean> = ref(false);
    const login: Ref<string> = ref(null);
    const password: Ref<string> = ref(null);
    const rememberMe: Ref<boolean> = ref(false);

    const router = useRouter();

    const accountService = inject<AccountService>('accountService');

    const storeToken = (jwt: string) => {
      if (rememberMe.value) {
        localStorage.setItem('jhi-authenticationToken', jwt);
        sessionStorage.removeItem('jhi-authenticationToken');
      } else {
        sessionStorage.setItem('jhi-authenticationToken', jwt);
        localStorage.removeItem('jhi-authenticationToken');
      }
    };

    const applyCachedTenantIfAny = async () => {
      const cached = TenantSwitchService.getCachedTenant();
      if (!cached) return;
      try {
        const tenantSwitchService = new TenantSwitchService();
        const myTenants = await tenantSwitchService.getMyTenants();
        const belongs = myTenants.data?.some((t: any) => t.id === cached.id);
        if (belongs) {
          const res = await tenantSwitchService.switchTenant(cached.id);
          const jwt = res.headers.authorization?.slice(7);
          if (jwt) {
            storeToken(jwt);
          }
        }
      } catch {
        // ignore, keep default tenant
      }
    };

    const doLogin = async () => {
      const data = { username: login.value, password: password.value, rememberMe: rememberMe.value };
      try {
        const result = await axios.post('api/authenticate', data);
        const bearerToken = result.headers.authorization;
        if (bearerToken && bearerToken.slice(0, 7) === 'Bearer ') {
          const jwt = bearerToken.slice(7, bearerToken.length);
          storeToken(jwt);
        }

        authenticationError.value = false;
        await applyCachedTenantIfAny();
        await accountService.retrieveAccount();
        const redirectUrl = sessionStorage.getItem('jhi-redirect-url');
        if (redirectUrl) {
          sessionStorage.removeItem('jhi-redirect-url');
          router.push(redirectUrl);
        } else {
          router.push('/');
        }
      } catch {
        authenticationError.value = true;
      }
    };
    return {
      authenticationError,
      login,
      password,
      rememberMe,
      accountService,
      doLogin,
    };
  },
});