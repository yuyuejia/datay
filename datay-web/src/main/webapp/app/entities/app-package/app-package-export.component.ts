import { defineComponent, inject, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import AppPackageService from './app-package.service';
import {
  type IAppPackage,
  type IAppPackageExportOptions,
  type IAppPackageExportRequest,
  pickHttpErrorMessage,
  summaryEntries,
} from '@/shared/model/app-package.model';
import { useAlertService } from '@/shared/alert/alert.service';

/**
 * 资产包导出向导：把当前租户已经开发完成的数据资产打包成一份可移植的资产包。
 */
export default defineComponent({
  name: 'AppPackageExport',
  setup() {
    const router = useRouter();
    const appPackageService = inject('appPackageService', () => new AppPackageService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const submitting = ref(false);
    const resultVisible = ref(false);
    const result = ref<IAppPackage | null>(null);

    const options = reactive<IAppPackageExportOptions>({
      dataSources: [],
      models: [],
      metrics: [],
      etlTasks: [],
      sqlJobs: [],
      dagJobs: [],
      categories: [],
    });

    const form = reactive<IAppPackageExportRequest>({
      name: '',
      code: '',
      description: '',
      category: '',
      version: '1.0.0',
      dataSourceIds: [],
      modelIds: [],
      metricIds: [],
      etlTaskIds: [],
      sqlJobIds: [],
      dagJobIds: [],
      includeReferences: true,
      saveToMarket: true,
      overwrite: false,
    });

    const resultSummary = ref<Array<{ label: string; value: number }>>([]);

    const loadOptions = async () => {
      try {
        const res = await appPackageService().getExportOptions();
        Object.assign(options, res);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    onMounted(loadOptions);

    const selectedCount = () =>
      form.dataSourceIds.length +
      form.modelIds.length +
      form.metricIds.length +
      form.etlTaskIds.length +
      form.sqlJobIds.length +
      form.dagJobIds.length;

    const submit = async () => {
      if (!form.name || !form.name.trim()) {
        alertService.showError('请填写资产包名称');
        return;
      }
      if (selectedCount() === 0) {
        alertService.showError('请至少选择一项要打包的资产');
        return;
      }
      submitting.value = true;
      try {
        const res = await appPackageService().exportPackage({ ...form });
        result.value = res;
        resultSummary.value = summaryEntries(res.itemSummary);
        resultVisible.value = true;
        alertService.showSuccess('资产包生成成功');
        await loadOptions();
      } catch (error: any) {
        alertService.showError(pickHttpErrorMessage(error, '导出资产包失败，请稍后重试'));
      } finally {
        submitting.value = false;
      }
    };

    const cancel = () => {
      router.push({ name: 'AppPackage' });
    };

    const goToMarket = () => {
      resultVisible.value = false;
      router.push({ name: 'AppPackage' });
    };

    return {
      submitting,
      resultVisible,
      result,
      resultSummary,
      options,
      form,
      submit,
      cancel,
      goToMarket,
    };
  },
});
