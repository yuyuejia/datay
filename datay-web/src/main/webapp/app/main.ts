import { computed, createApp, h, provide } from 'vue';
import { createPinia, storeToRefs } from 'pinia';
import ElementPlus from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import 'element-plus/dist/index.css';

import App from './app.vue';
import router from './router';
import { useStore } from '@/store';
import { setupAxiosInterceptors } from '@/shared/config/axios-interceptor';

import { initFortAwesome } from '@/shared/config/config';
import AppModal from '@/shared/ui/AppModal.vue';
import JhiItemCountComponent from '@/shared/jhi-item-count.vue';
import JhiSortIndicatorComponent from '@/shared/sort/jhi-sort-indicator.vue';
import { useLoginModal } from '@/account/login-modal';
import AccountService from '@/account/account.service';

import '../content/scss/global.scss';
import '../content/scss/vendor.scss';

const pinia = createPinia();

// jhipster-needle-add-entity-service-to-main-import - JHipster will import entities services here

const app = createApp({
  setup() {
    const { showLogin } = useLoginModal();
    const store = useStore();
    const accountService = new AccountService(store);
    provide(
      'currentLanguage',
      computed(() => store.account?.langKey ?? navigator.language ?? 'zh-cn'),
    );

    router.beforeResolve(async (to, from, next) => {
      if (!store.authenticated) {
        await accountService.update();
      }
      if (to.name === 'Login' && store.authenticated) {
        next({ path: '/' });
        return;
      }
      if (!store.authenticated && !to.meta.public) {
        if (to.path !== '/login') {
          sessionStorage.setItem('jhi-redirect-url', to.fullPath);
        }
        next({ path: '/login' });
        return;
      }
      if (to.meta?.authorities && to.meta.authorities.length > 0) {
        const value = await accountService.hasAnyAuthorityAndCheckAuth(to.meta.authorities);
        if (!value) {
          next({ path: '/forbidden' });
          return;
        }
      }
      next();
    });

    setupAxiosInterceptors(
      error => {
        const url = error.response?.config?.url;
        const status = error.status || error.response.status;
        if (status === 401) {
          // Store logged out state.
          store.logout();
          if (!url.endsWith('api/account') && !url.endsWith('api/authenticate')) {
            if (router.currentRoute.value.path !== '/login') {
              sessionStorage.setItem('jhi-redirect-url', router.currentRoute.value.fullPath);
            }
            // Ask for a new authentication
            showLogin();
            return;
          }
        }
        return Promise.reject(error);
      },
      error => {
        return Promise.reject(error);
      },
    );

    const { authenticated } = storeToRefs(store);
    provide('authenticated', authenticated);
    provide(
      'currentUsername',
      computed(() => store.account?.login),
    );

    provide('accountService', accountService);
    // jhipster-needle-add-entity-service-to-main - JHipster will import entities services here
  },
  render: () => h(App),
});

initFortAwesome(app);

app
  .component('jhi-item-count', JhiItemCountComponent)
  .component('jhi-sort-indicator', JhiSortIndicatorComponent)
  .component('AppModal', AppModal)
  .use(router)
  .use(pinia)
  .use(ElementPlus, { locale: zhCn })
  .mount('#app');