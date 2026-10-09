import { type Ref, defineComponent, inject, onMounted, onUnmounted, ref, watch } from 'vue';

import DataSourceService from '@/entities/data-source/data-source.service';
import DataApiService from './data-api.service';
import { type IDataApi, parseApiConfig } from '@/shared/model/data-api.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

export default defineComponent({
  name: 'DataApi',
  setup() {
    const dateFormat = useDateFormat();
    const dataApiService = inject('dataApiService', () => new DataApiService());
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref('');

    const dataApis: Ref<IDataApi[]> = ref([]);
    const dataSourceNames = ref<Record<string, string>>({});

    const isFetching = ref(false);

    const loadDataSources = async () => {
      try {
        const res = await dataSourceService().retrieve({ page: 0, size: 200 });
        const names: Record<string, string> = {};
        (res.data || []).forEach((item: any) => {
          names[item.id] = item.name;
        });
        dataSourceNames.value = names;
      } catch (err) {
        // 数据源列表加载失败时静默忽略，不影响服务列表展示
      }
    };

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

    const retrieveDataApis = async () => {
      isFetching.value = true;
      try {
        const paginationQuery = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
          search: search.value,
        };
        const res = await dataApiService().retrieve(paginationQuery);
        totalItems.value = Number(res.headers['x-total-count']);
        queryCount.value = totalItems.value;
        dataApis.value = res.data;
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
          retrieveDataApis();
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
      await loadDataSources();
      await retrieveDataApis();
    });

    const removeId: Ref<string> = ref(null);
    const removeEntity = ref<any>(null);
    const prepareRemove = (instance: IDataApi) => {
      removeId.value = instance.id;
      removeEntity.value.show();
    };
    const closeDialog = () => {
      removeEntity.value.hide();
    };
    const removeDataApi = async () => {
      try {
        await dataApiService().delete(removeId.value);
        alertService.showInfo(`数据服务已删除：${removeId.value}`);
        removeId.value = null;
        retrieveDataApis();
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
        await retrieveDataApis();
      } else {
        clear();
      }
    });

    watch(page, async () => {
      await retrieveDataApis();
    });

    // 调用说明弹窗
    const docApi = ref<IDataApi | null>(null);
    const docUrl = ref('');
    const docMethod = ref('GET / POST');
    const docCurl = ref('');
    const docModalVisible = ref(false);

    const buildCurl = (row: IDataApi, url: string, method: string): string => {
      if (row.sourceType === 'API') {
        const verbMethod = method.toUpperCase();
        const verb = verbMethod === 'GET' ? '' : `-X ${verbMethod} `;
        const hasBody = verbMethod === 'POST' || verbMethod === 'PUT' || verbMethod === 'PATCH';
        const body = hasBody ? ` \\\n  -H "Content-Type: application/json" \\\n  -d '{"param":"value"}'` : '';
        return [
          '# Header 鉴权',
          `curl ${verb}"${url}" \\`,
          `  -H "Authorization: Bearer mcp_xxx"${body}`,
          '',
          '# Query 鉴权',
          `curl ${verb}"${url}?access_token=mcp_xxx"${body}`,
        ].join('\n');
      }
      if (row.sourceType === 'SQL') {
        return [
          '# Header 鉴权',
          `curl "${url}?param=value" \\`,
          '  -H "Authorization: Bearer mcp_xxx"',
          '',
          '# Query 鉴权',
          `curl "${url}?param=value&access_token=mcp_xxx"`,
        ].join('\n');
      }
      return [
        '# Header 鉴权',
        `curl "${url}?status=PAID&pageNum=1&pageSize=20" \\`,
        '  -H "Authorization: Bearer mcp_xxx"',
        '',
        '# Query 鉴权',
        `curl "${url}?status=PAID&pageNum=1&pageSize=20&access_token=mcp_xxx"`,
      ].join('\n');
    };

    const openDoc = (row: IDataApi) => {
      docApi.value = row;
      docUrl.value = `${window.location.origin}/open-api/data/${row.code}`;
      docMethod.value = row.sourceType === 'API' ? parseApiConfig(row.apiConfig)?.method || 'GET' : 'GET / POST';
      docCurl.value = buildCurl(row, docUrl.value, docMethod.value);
      docModalVisible.value = true;
    };

    const sourceTypeLabel = (type: string | null | undefined) => {
      if (type === 'SQL') {
        return 'SQL';
      }
      if (type === 'API') {
        return '已有 API';
      }
      return '数据表';
    };

    const statusLabel = (status: string | null | undefined) => {
      return status === 'ENABLED' ? '启用' : '停用';
    };

    const tableDisplay = (row: IDataApi) => {
      if (row.sourceType === 'SQL') {
        return row.sqlText ? row.sqlText.replace(/\s+/g, ' ').slice(0, 40) : '-';
      }
      if (row.sourceType === 'API') {
        const config = parseApiConfig(row.apiConfig);
        return config?.url || '-';
      }
      return row.schemaName && row.tableName ? `${row.schemaName}.${row.tableName}` : '-';
    };

    return {
      dataApis,
      dataSourceNames,
      isFetching,
      retrieveDataApis,
      clear,
      search,
      ...dateFormat,
      removeId,
      removeEntity,
      prepareRemove,
      closeDialog,
      removeDataApi,
      itemsPerPage,
      queryCount,
      page,
      propOrder,
      reverse,
      totalItems,
      changeOrder,
      handleSortChange,
      docApi,
      docUrl,
      docMethod,
      docCurl,
      docModalVisible,
      openDoc,
      sourceTypeLabel,
      statusLabel,
      tableDisplay,
    };
  },
});
