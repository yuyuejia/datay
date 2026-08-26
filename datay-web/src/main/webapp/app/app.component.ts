import { defineComponent, provide, computed } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute } from 'vue-router';

import { useLoginModal } from '@/account/login-modal';
import LoginForm from '@/account/login-form/login-form.vue';
import Ribbon from '@/core/ribbon/ribbon.vue';
import JhiFooter from '@/core/jhi-footer/jhi-footer.vue';
import JhiNavbar from '@/core/jhi-navbar/jhi-navbar.vue';
import { useAlertService } from '@/shared/alert/alert.service';
import '@/shared/config/dayjs';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'App',
  components: {
    ribbon: Ribbon,
    'jhi-navbar': JhiNavbar,
    'login-form': LoginForm,
    'jhi-footer': JhiFooter,
  },
  setup() {
    provide('alertService', useAlertService());
    const { loginModalOpen } = storeToRefs(useLoginModal());

    const route = useRoute();
    const isDesignPage = computed(() => {
      return route.name === 'ETLTaskDesign' || route.name === 'ETLTaskDesignNew' || route.name === 'ETLTaskCreate';
    });

    return {
      loginModalOpen,
      isDesignPage,
    };
  },
});
