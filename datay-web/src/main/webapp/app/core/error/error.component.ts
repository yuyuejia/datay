import { type ComputedRef, type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useLoginModal } from '@/account/login-modal';

export default defineComponent({
  name: 'Error',
  setup() {
    const { showLogin } = useLoginModal();
    const authenticated = inject<ComputedRef<boolean>>('authenticated');
    const errorMessage: Ref<string> = ref(null);
    const error403: Ref<boolean> = ref(false);
    const error404: Ref<boolean> = ref(false);
    const route = useRoute();

    if (route.meta) {
      errorMessage.value = route.meta.errorMessage ?? null;
      error403.value = route.meta.error403 ?? false;
      error404.value = route.meta.error404 ?? false;
      if (!authenticated.value && error403.value) {
        showLogin();
      }
    }

    const statusCode = computed(() => {
      if (error404.value) return '404';
      if (error403.value) return '403';
      return '错误';
    });

    const errorTitle = computed(() => {
      if (error404.value) return '页面走丢了';
      if (error403.value) return '无权访问';
      return '出错了';
    });

    const errorDescription = computed(() => {
      if (error404.value) return '抱歉，您访问的页面不存在或已被移除，请检查地址是否正确。';
      if (error403.value) return '抱歉，您没有访问该页面的权限。如需访问，请联系管理员。';
      return errorMessage.value ?? '抱歉，页面出现了一些问题，请稍后重试。';
    });

    const errorIcon = computed(() => {
      if (error404.value) return 'search';
      if (error403.value) return 'lock';
      return 'exclamation-circle';
    });

    const goBack = () => {
      if (window.history.length > 1) {
        window.history.back();
      } else {
        window.location.href = '/';
      }
    };

    return {
      errorMessage,
      error403,
      error404,
      statusCode,
      errorTitle,
      errorDescription,
      errorIcon,
      goBack,
    };
  },
});
