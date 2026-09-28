import { type Ref, computed, defineComponent, inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import DataSourceService from './data-source.service';
import { useDateFormat } from '@/shared/composables';
import { type IDataSource } from '@/shared/model/data-source.model';
import { useAlertService } from '@/shared/alert/alert.service';

import DataSourceModal from './data-source-modal.vue';

export default defineComponent({
  name: 'DataSourceDetails',
  components: { DataSourceModal },
  setup() {
    const dateFormat = useDateFormat();
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const route = useRoute();
    const router = useRouter();

    const previousState = () => router.go(-1);
    const dataSource: Ref<IDataSource> = ref({});

    // DuckDB / DuckLake 通过本地文件或挂载方式访问，无需用户名密码
    const requiresCredentials = computed(() => !['DUCKDB', 'DUCKLAKE'].includes(dataSource.value.type ?? ''));

    const retrieveDataSource = async dataSourceId => {
      try {
        const res = await dataSourceService().find(dataSourceId);
        dataSource.value = res;
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    if (route.params?.dataSourceId) {
      retrieveDataSource(route.params.dataSourceId);
    }

    const modalShow = ref(false);

    const openEditModal = () => {
      modalShow.value = true;
    };

    const onModalSaved = () => {
      if (dataSource.value.id) {
        retrieveDataSource(dataSource.value.id);
      }
    };

    return {
      ...dateFormat,
      alertService,
      dataSource,
      previousState,
      requiresCredentials,
      modalShow,
      openEditModal,
      onModalSaved,
    };
  },
});