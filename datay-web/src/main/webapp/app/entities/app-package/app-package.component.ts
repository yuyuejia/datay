import { type Ref, defineComponent, inject, onMounted, onUnmounted, ref, watch } from 'vue';

import AppPackageService from './app-package.service';
import { type IAppPackage, type IAppPackageInstance, pickHttpErrorMessage, summaryEntries } from '@/shared/model/app-package.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import AppPackageInit from './app-package-init.vue';

/**
 * 数据服务应用市场：浏览系统预制与租户导出的数据应用资产包，并基于资产包初始化数据应用。
 */
export default defineComponent({
  name: 'AppPackage',
  components: { AppPackageInit },
  setup() {
    const dateFormat = useDateFormat();
    const appPackageService = inject('appPackageService', () => new AppPackageService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const activeTab = ref('packages');

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const totalItems = ref(0);
    const search = ref('');
    const source = ref('ALL');
    const category = ref('');
    const categories = ref<string[]>([]);
    const packages: Ref<IAppPackage[]> = ref([]);
    const isFetching = ref(false);

    const instancePage = ref(1);
    const instanceTotal = ref(0);
    const instanceItemsPerPage = ref(20);
    const instances: Ref<IAppPackageInstance[]> = ref([]);

    const initVisible = ref(false);
    const initPackage: Ref<IAppPackage | null> = ref(null);
    const initContent: Ref<string | null> = ref(null);
    const fileInput = ref<HTMLInputElement | null>(null);

    const clear = () => {
      page.value = 1;
    };

    const retrievePackages = async () => {
      isFetching.value = true;
      try {
        const res = await appPackageService().retrieve(
          { page: page.value - 1, size: itemsPerPage.value, sort: ['id,desc'] },
          { search: search.value, source: source.value, category: category.value }
        );
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        packages.value = res.data;
      } catch (error: any) {
        alertService.showHttpError(error.response);
      } finally {
        isFetching.value = false;
      }
    };

    const retrieveInstances = async () => {
      try {
        const res = await appPackageService().retrieveInstances({
          page: instancePage.value - 1,
          size: instanceItemsPerPage.value,
          sort: ['id,desc'],
        });
        instanceTotal.value = Number(res.headers['x-total-count']);
        instances.value = res.data;
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const loadCategories = async () => {
      try {
        categories.value = await appPackageService().getCategories();
      } catch (error) {
        categories.value = [];
      }
    };

    let searchTimer: ReturnType<typeof setTimeout> | null = null;
    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        if (page.value === 1) {
          retrievePackages();
        } else {
          clear();
        }
      }, 300);
    });

    onUnmounted(() => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
    });

    watch([source, category, itemsPerPage], () => clear());
    watch(page, () => retrievePackages());
    watch(instancePage, () => retrieveInstances());
    watch(activeTab, (value) => {
      if (value === 'instances') {
        retrieveInstances();
      }
    });

    onMounted(async () => {
      await Promise.all([loadCategories(), retrievePackages()]);
      await retrieveInstances();
    });

    // ---------------- 操作 ----------------

    const openInit = (row: IAppPackage) => {
      initPackage.value = row;
      initContent.value = null;
      initVisible.value = true;
    };

    const onInitialized = async () => {
      await Promise.all([retrievePackages(), retrieveInstances(), loadCategories()]);
    };

    const download = async (row: IAppPackage) => {
      try {
        const blob = await appPackageService().download(row.id);
        const url = window.URL.createObjectURL(new Blob([blob], { type: 'application/json' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = `${row.code || 'app-package'}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } catch (error: any) {
        alertService.showHttpError(error.response);
      }
    };

    const triggerUpload = () => {
      fileInput.value?.click();
    };

    const onFileSelected = async (event: Event) => {
      const input = event.target as HTMLInputElement;
      const file = input?.files?.[0];
      input.value = '';
      if (!file) {
        return;
      }
      const text = await file.text();
      try {
        JSON.parse(text);
      } catch (error) {
        alertService.showError('所选文件不是合法的 JSON 资产包');
        return;
      }
      initPackage.value = null;
      initContent.value = text;
      initVisible.value = true;
    };

    const removeId: Ref<string> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (row: IAppPackage) => {
      removeId.value = row.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removePackage = async () => {
      try {
        await appPackageService().delete(removeId.value);
        alertService.showInfo(`资产包已删除：${removeId.value}`);
        removeId.value = null;
        closeDialog();
        await Promise.all([retrievePackages(), loadCategories()]);
      } catch (error: any) {
        alertService.showError(pickHttpErrorMessage(error, '删除资产包失败，请稍后重试'));
      }
    };

    const summaryText = (row: IAppPackage) => {
      const entries = summaryEntries(row.itemSummary);
      if (entries.length === 0) {
        return '-';
      }
      return entries.map((entry) => `${entry.label} ${entry.value}`).join(' · ');
    };

    const sourceLabel = (row: IAppPackage) => (row.packageType === 'SYSTEM' ? '系统预制' : '本租户导出');

    const sizeText = (row: IAppPackage) => {
      if (!row.contentSize) {
        return '-';
      }
      return row.contentSize > 1024 ? `${(row.contentSize / 1024).toFixed(1)} KB` : `${row.contentSize} B`;
    };

    return {
      activeTab,
      itemsPerPage,
      queryCount,
      page,
      totalItems,
      search,
      source,
      category,
      categories,
      packages,
      isFetching,
      instancePage,
      instanceTotal,
      instanceItemsPerPage,
      instances,
      initVisible,
      initPackage,
      initContent,
      fileInput,
      retrievePackages,
      clear,
      openInit,
      onInitialized,
      download,
      triggerUpload,
      onFileSelected,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removePackage,
      summaryText,
      sourceLabel,
      sizeText,
      ...dateFormat,
    };
  },
});
