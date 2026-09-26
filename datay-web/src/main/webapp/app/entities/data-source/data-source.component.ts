import { type Ref, defineComponent, inject, onMounted, onUnmounted, ref, watch } from 'vue';

import DataSourceService from './data-source.service';
import { type IDataSource } from '@/shared/model/data-source.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

import DataSourceModal from './data-source-modal.vue';

export default defineComponent({
  name: 'DataSource',
  components: { DataSourceModal },
  setup() {
    const dateFormat = useDateFormat();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref('');

    const dataSources: Ref<IDataSource[]> = ref([]);
    const defaultDataSourceId: Ref<number | null> = ref(null);

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

    const retrieveDefaultWarehouse = async () => {
      try {
        const res = await dataSourceService().getDefaultWarehouse();
        defaultDataSourceId.value = res.data?.dataSourceId ?? null;
      } catch (err) {
        console.warn('加载默认数仓失败', err);
      }
    };

    const isDefaultWarehouse = (row: IDataSource): boolean => {
      return row.id != null && row.id === defaultDataSourceId.value;
    };

    const defaultWarehouseModal = ref<any>(null);
    const defaultWarehouseOptions: Ref<IDataSource[]> = ref([]);
    const selectedDefaultId: Ref<number | null> = ref(null);

    const openDefaultDialog = async () => {
      selectedDefaultId.value = defaultDataSourceId.value;
      try {
        const res = await dataSourceService().retrieve({ page: 0, size: 1000, sort: ['id,asc'] });
        defaultWarehouseOptions.value = res.data || [];
      } catch (err) {
        alertService.showHttpError(err.response);
      }
      defaultWarehouseModal.value?.show();
    };

    const closeDefaultDialog = () => {
      defaultWarehouseModal.value?.hide();
    };

    const saveDefaultWarehouse = async () => {
      if (selectedDefaultId.value == null) {
        alertService.showWarning('请选择数据源');
        return;
      }
      try {
        await dataSourceService().setDefaultWarehouse(selectedDefaultId.value);
        defaultDataSourceId.value = selectedDefaultId.value;
        alertService.showSuccess('默认数仓设置成功');
        closeDefaultDialog();
      } catch (err) {
        alertService.showHttpError(err.response);
      }
    };

    const retrieveDataSources = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
          search: search.value,
        };
        const res = await dataSourceService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        dataSources.value = res.data;
      } catch (err) {
        alertService.showHttpError(err.response);
      } finally {
        isFetching.value = false;
      }
    };

    let searchTimer: ReturnType<typeof setTimeout> | null = null;

    watch(search, () => {
      if (searchTimer) {
        clearTimeout(searchTimer);
      }
      searchTimer = setTimeout(() => {
        if (page.value === 1) {
          retrieveDataSources();
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

    onMounted(async () => {
      await Promise.all([retrieveDataSources(), retrieveDefaultWarehouse()]);
    });

    const removeId: Ref<number> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IDataSource) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeDataSource = async () => {
      try {
        await dataSourceService().delete(removeId.value);
        const message = `A DataSource is deleted with identifier ${removeId.value}`;
        alertService.showInfo(message, { variant: 'danger' });
        removeId.value = null;
        retrieveDataSources();
        retrieveDefaultWarehouse();
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

    watch([propOrder, reverse, itemsPerPage], async () => {
      if (page.value === 1) {
        await retrieveDataSources();
      } else {
        clear();
      }
    });

    watch(page, async () => {
      await retrieveDataSources();
    });

    const modalShow = ref(false);
    const modalMode = ref<'create' | 'edit'>('create');
    const modalDataSourceId = ref<number | null>(null);

    const openCreateModal = () => {
      modalMode.value = 'create';
      modalDataSourceId.value = null;
      modalShow.value = true;
    };

    const openEditModal = (row: IDataSource) => {
      modalMode.value = 'edit';
      modalDataSourceId.value = row.id;
      modalShow.value = true;
    };

    const onModalSaved = () => {
      retrieveDataSources();
    };

    return {
      dataSources,
      defaultDataSourceId,
      isDefaultWarehouse,
      defaultWarehouseModal,
      defaultWarehouseOptions,
      selectedDefaultId,
      openDefaultDialog,
      closeDefaultDialog,
      saveDefaultWarehouse,
      retrieveDefaultWarehouse,
      isFetching,
      retrieveDataSources,
      clear,
      search,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeDataSource,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      handleSortChange,
      modalShow,
      modalMode,
      modalDataSourceId,
      openCreateModal,
      openEditModal,
      onModalSaved,
    };
  },
});