import { type Ref, defineComponent, inject, onMounted, ref, watch } from 'vue';

import DataSyncService from './data-sync.service';
import { type IDataSync } from '@/shared/model/data-sync.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  compatConfig: { MODE: 3 },
  name: 'DataSync',
  setup() {
    const dateFormat = useDateFormat();
    const dataSyncService = inject('dataSyncService', () => new DataSyncService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);

    const dataSyncs: Ref<IDataSync[]> = ref([]);

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

    const retrieveDataSyncs = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
        };
        const res = await dataSyncService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        dataSyncs.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    const handleSyncList = () => {
      retrieveDataSyncs();
    };

    onMounted(async () => {
      await retrieveDataSyncs();
    });

    const removeId: Ref<number> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IDataSync) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeDataSync = async () => {
      try {
        await dataSyncService().delete(removeId.value);
        const message = `A DataSync is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveDataSyncs();
        closeDialog();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const changeOrder = (newOrder: string) => {
      const [prop, order] = newOrder.split(',');
      if (propOrder.value === prop) {
        reverse.value = order === 'desc';
      } else {
        propOrder.value = prop;
        reverse.value = order === 'desc';
      }
    };

    const handleSortChange = (column: { prop: string; order: 'ascending' | 'descending' | null }) => {
      if (column.prop && column.order) {
        const order = column.order === 'ascending' ? 'asc' : 'desc';
        changeOrder(`${column.prop},${order}`);
      }
    };

    const executeDataSync = async (row: IDataSync) => {
      try {
        await dataSyncService().execute(row.id);
        alertService.showInfo(`任务 ${row.jobName} 已提交执行`);
        retrieveDataSyncs();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    // Whenever order changes, reset the pagination
    watch([propOrder, reverse], async () => {
      if (page.value === 1) {
        // first page, retrieve new data
        await retrieveDataSyncs();
      } else {
        // reset the pagination
        clear();
      }
    });

    // Whenever page changes, switch to the new page.
    watch(page, async () => {
      await retrieveDataSyncs();
    });

    return {
      dataSyncs,
      handleSyncList,
      isFetching,
      retrieveDataSyncs,
      clear,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeDataSync,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      handleSortChange, // 新增方法
      executeDataSync,
    };
  },
});