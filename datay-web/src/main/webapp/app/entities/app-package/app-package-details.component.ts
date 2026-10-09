import { computed, defineComponent, inject, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import AppPackageService from './app-package.service';
import { type IAppPackage, parsePackageContent, summaryEntries } from '@/shared/model/app-package.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import AppPackageInit from './app-package-init.vue';

/**
 * 资产包详情：预览资产包内的数据源、模型、指标与任务，并可直接发起初始化。
 */
export default defineComponent({
  name: 'AppPackageDetails',
  components: { AppPackageInit },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const dateFormat = useDateFormat();
    const appPackageService = inject('appPackageService', () => new AppPackageService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const appPackage = ref<IAppPackage | null>(null);
    const initVisible = ref(false);

    const content = computed(() => parsePackageContent(appPackage.value?.content));
    const summary = computed(() => {
      const fromContent = content.value?.summary;
      if (fromContent && Object.keys(fromContent).length > 0) {
        return summaryEntries(fromContent);
      }
      return summaryEntries(appPackage.value?.itemSummary);
    });

    const retrieve = async () => {
      const id = route.params.packageId ? String(route.params.packageId) : '';
      if (!id) {
        alertService.showError('资产包 ID 不存在');
        return;
      }
      try {
        appPackage.value = await appPackageService().find(id);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const download = async () => {
      if (!appPackage.value?.id) {
        return;
      }
      try {
        const blob = await appPackageService().download(appPackage.value.id);
        const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/json' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = `${appPackage.value.code || 'app-package'}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const back = () => router.push({ name: 'AppPackage' });

    const onInitialized = () => {
      initVisible.value = false;
    };

    onMounted(retrieve);

    return {
      appPackage,
      content,
      summary,
      initVisible,
      download,
      back,
      onInitialized,
      ...dateFormat,
    };
  },
});
