import { type Ref, defineComponent, inject, onMounted, ref, watch } from 'vue';

import ServiceConfigService from './service-config.service';
import { type IServiceConfig, ServiceConfig } from '@/shared/model/service-config.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'ServiceConfig',
  setup() {
    const dateFormat = useDateFormat();
    const serviceConfigService = inject('serviceConfigService', () => new ServiceConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);

    const serviceConfigs: Ref<IServiceConfig[]> = ref([]);

    const isFetching = ref(false);

    const clear = () => {
      page.value = 1;
    };

    const sort = (): Array<any> => {
      const result = [`${propOrder.value},${reverse.value ? 'desc' : 'asc'}`];
      if (propOrder.value !== 'id') {
        result.push('id');
      }
      return result;
    };

    const retrieveServiceConfigs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await serviceConfigService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        serviceConfigs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveServiceConfigs();
    };

    onMounted(async () => {
      await retrieveServiceConfigs();
    });

    const removeId: Ref<string> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IServiceConfig) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeServiceConfig = async () => {
      try {
        await serviceConfigService().delete(removeId.value);
        const message = `服务配置 ${removeId.value} 已删除`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveServiceConfigs();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const changeOrder = (newOrder: string) => {
      if (propOrder.value === newOrder) {
        reverse.value = !reverse.value;
      } else {
        reverse.value = false;
      }
      propOrder.value = newOrder;
    };

    const handleSortChange = (column: { prop: string; order: 'ascending' | 'descending' | null }) => {
      if (!column.prop || !column.order) {
        return;
      }
      const order = column.order === 'ascending' ? 'asc' : 'desc';
      if (propOrder.value !== column.prop) {
        propOrder.value = column.prop;
      }
      reverse.value = order === 'desc';
    };

    const editEntity = ref<any>(null);
    const editServiceConfig: Ref<IServiceConfig> = ref(new ServiceConfig());
    const isSaving = ref(false);

    const openCreateModal = () => {
      editServiceConfig.value = new ServiceConfig();
      editEntity.value.show();
    };

    const closeEditDialog = () => {
      editEntity.value.hide();
    };

    const saveServiceConfig = async () => {
      const { dfGroup, dfKey, dfValue } = editServiceConfig.value;
      if (!dfGroup || !dfKey) {
        alertService.showWarning('请填写配置分组和配置项');
        return;
      }
      isSaving.value = true;
      try {
        const res = await serviceConfigService().create({
          dfGroup,
          dfKey,
          dfValue,
        });
        alertService.showSuccess(`服务配置 ${res.id} 已创建`);
        closeEditDialog();
        retrieveServiceConfigs();
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        isSaving.value = false;
      }
    };

    // Whenever order changes, reset the pagination
    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        // first page, retrieve new data
        await retrieveServiceConfigs();
      } else {
        // reset the pagination
        clear();
      }
    });

    // Whenever page changes, switch to the new page.
    watch(page, async () => {
      await retrieveServiceConfigs();
    });

    return {
      serviceConfigs,
      handleSyncList,
      isFetching,
      retrieveServiceConfigs,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeServiceConfig,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      handleSortChange,
      editEntity,
      editServiceConfig,
      isSaving,
      openCreateModal,
      closeEditDialog,
      saveServiceConfig,
    };
  },
});
