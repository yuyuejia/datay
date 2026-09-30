import { type Ref, computed, defineComponent, inject, onMounted, ref, watch } from 'vue';
import axios from 'axios';

import { SERVICE_CONFIG_GROUPS, type ConfigField, type ConfigGroup } from './service-config.groups';
import DataSourceService from '@/entities/data-source/data-source.service';
import { type IDataSource } from '@/shared/model/data-source.model';

interface ITenantContext {
  tenantId?: number | null;
  tenantCode?: string | null;
  tenantName?: string | null;
}

export default defineComponent({
  name: 'ServiceConfigSettings',
  setup() {
    const alertService = inject<any>('alertService');

    const groups: ConfigGroup[] = SERVICE_CONFIG_GROUPS;
    const activeKey = ref(groups[0].key);
    const activeGroup = computed<ConfigGroup>(() => groups.find(group => group.key === activeKey.value) ?? groups[0]);

    const values: Ref<Record<string, any>> = ref({});
    const tenant: Ref<ITenantContext> = ref({});
    const dataSourceOptions: Ref<IDataSource[]> = ref([]);

    const isLoading = ref(false);
    const isSaving = ref(false);
    const isTesting = ref(false);

    const visibleFields = computed<ConfigField[]>(() =>
      activeGroup.value.fields.filter(field => !field.showWhen || field.showWhen(values.value))
    );

    const loadTenant = async () => {
      try {
        const res = await axios.get('api/tenant-settings/context');
        tenant.value = res.data ?? {};
      } catch {
        tenant.value = {};
      }
    };

    const loadDataSources = async () => {
      try {
        const res = await new DataSourceService().retrieve({ page: 0, size: 1000, sort: ['id,asc'] });
        dataSourceOptions.value = res.data ?? [];
      } catch (error: any) {
        alertService?.showHttpError(error.response);
      }
    };

    const loadGroup = async () => {
      isLoading.value = true;
      try {
        values.value = await activeGroup.value.load();
      } catch (error: any) {
        values.value = {};
        alertService?.showError(error?.response?.data?.error || error?.message || '加载配置失败');
      } finally {
        isLoading.value = false;
      }
    };

    const saveGroup = async () => {
      if (isSaving.value) {
        return;
      }
      isSaving.value = true;
      try {
        await activeGroup.value.save(values.value);
        alertService?.showSuccess('保存成功');
        await loadGroup();
      } catch (error: any) {
        alertService?.showError(error?.response?.data?.error || error?.message || '保存失败');
      } finally {
        isSaving.value = false;
      }
    };

    const testGroup = async () => {
      const test = activeGroup.value.test;
      if (!test || isTesting.value) {
        return;
      }
      isTesting.value = true;
      try {
        const result = await test();
        if (result?.success === false) {
          alertService?.showError(result.error || '测试连接失败');
        } else {
          alertService?.showSuccess(`测试连接成功${result?.strategy ? `（${result.strategy}）` : ''}`);
        }
      } catch (error: any) {
        alertService?.showError(error?.response?.data?.error || error?.message || '测试连接失败');
      } finally {
        isTesting.value = false;
      }
    };

    watch(activeKey, () => {
      void loadGroup();
    });

    onMounted(async () => {
      await Promise.all([loadTenant(), loadDataSources()]);
      await loadGroup();
    });

    const tenantLabel = computed(() => {
      if (tenant.value.tenantName && tenant.value.tenantCode) {
        return `${tenant.value.tenantName}（${tenant.value.tenantCode}）`;
      }
      return tenant.value.tenantName || tenant.value.tenantCode || `租户 #${tenant.value.tenantId ?? '-'}`;
    });

    return {
      groups,
      activeKey,
      activeGroup,
      visibleFields,
      values,
      tenant,
      tenantLabel,
      dataSourceOptions,
      isLoading,
      isSaving,
      isTesting,
      saveGroup,
      testGroup,
    };
  },
});
