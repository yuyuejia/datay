import { type Ref, defineComponent, inject, onMounted, ref, watch } from 'vue';

import DataSyncTableConfigService from './data-sync-table-config.service';
import { type IDataSyncTableConfig } from '@/shared/model/data-sync-table-config.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'DataSyncTableConfig',
  setup() {
    const dateFormat = useDateFormat();
    const dataSyncTableConfigService = inject('dataSyncTableConfigService', () => new DataSyncTableConfigService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);

    const dataSyncTableConfigs: Ref<IDataSyncTableConfig[]> = ref([]);

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

    const retrieveDataSyncTableConfigs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await dataSyncTableConfigService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        dataSyncTableConfigs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveDataSyncTableConfigs();
    };

    onMounted(async () => {
      await retrieveDataSyncTableConfigs();
    });

    const removeId: Ref<string> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IDataSyncTableConfig) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeDataSyncTableConfig = async () => {
      try {
        await dataSyncTableConfigService().delete(removeId.value);
        const message = `A DataSyncTableConfig is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveDataSyncTableConfigs();
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

    // Whenever order changes, reset the pagination
    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        // first page, retrieve new data
        await retrieveDataSyncTableConfigs();
      } else {
        // reset the pagination
        clear();
      }
    });

    // Whenever page changes, switch to the new page.
    watch(page, async () => {
      await retrieveDataSyncTableConfigs();
    });

    return {
      dataSyncTableConfigs,
      handleSyncList,
      isFetching,
      retrieveDataSyncTableConfigs,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeDataSyncTableConfig,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
    };
  },
});
