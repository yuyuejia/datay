import { type Ref, computed, defineComponent, inject, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessageBox } from 'element-plus';

import DataSourceService from '@/entities/data-source/data-source.service';
import ETLTaskDirectoryService from '@/entities/etl-task/etl-task-directory.service';
import DataApiService from './data-api.service';
import { type IDataApi, parseApiConfig, DATA_API_SOURCE_TYPE_DATASET } from '@/shared/model/data-api.model';
import { ETLTaskDirectory, type IETLTaskDirectory } from '@/shared/model/etl-task-directory.model';
import { useDateFormat } from '@/shared/composables';
import { useAlertService } from '@/shared/alert/alert.service';

/** 数据服务目录在公共目录表中的类型标识。 */
const DIRECTORY_TASK_TYPE = 'DATA_API';

interface IDirectoryTreeNode {
  id: string;
  label: string;
  type: 'directory';
  data: IETLTaskDirectory;
  children?: IDirectoryTreeNode[];
}

export default defineComponent({
  name: 'DataApi',
  setup() {
    const dateFormat = useDateFormat();
    const dataApiService = inject('dataApiService', () => new DataApiService());
    const dataSourceService = inject('dataSourceService', () => new DataSourceService());
    const etlTaskDirectoryService = inject('etlTaskDirectoryService', () => new ETLTaskDirectoryService());
    const alertService = inject('alertService', () => useAlertService(), true);

    const itemsPerPage = ref(20);
    const queryCount: Ref<number> = ref(null);
    const page: Ref<number> = ref(1);
    const propOrder = ref('id');
    const reverse = ref(false);
    const totalItems = ref(0);
    const search = ref('');

    // ---------------- 数据服务目录 ----------------

    const directories: Ref<IETLTaskDirectory[]> = ref([]);
    const directoryLoading = ref(false);
    const selectedDirectoryId: Ref<string | null> = ref(null);
    const treeProps = { children: 'children', label: 'label' };
    const directoryTreeRef = ref<any>(null);

    const compareDirectories = (a: IETLTaskDirectory, b: IETLTaskDirectory): number =>
      (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || String(a.name || '').localeCompare(String(b.name || ''));

    const directoryTree = computed<IDirectoryTreeNode[]>(() => {
      const byParent = new Map<string, IETLTaskDirectory[]>();
      directories.value.forEach(directory => {
        const parentId = directory.parentId || '';
        if (!byParent.has(parentId)) {
          byParent.set(parentId, []);
        }
        byParent.get(parentId)!.push(directory);
      });
      const build = (parentId: string): IDirectoryTreeNode[] => {
        const nodes = (byParent.get(parentId) || [])
          .slice()
          .sort(compareDirectories)
          .map(directory => {
            const children = build(directory.id!);
            const node: IDirectoryTreeNode = {
              id: directory.id!,
              label: directory.name || '',
              type: 'directory',
              data: directory,
            };
            if (children.length > 0) {
              node.children = children;
            }
            return node;
          });
        return nodes;
      };
      return build('');
    });

    const siblingDirectories = (directory: IETLTaskDirectory): IETLTaskDirectory[] =>
      directories.value
        .filter(item => (item.parentId || '') === (directory.parentId || ''))
        .slice()
        .sort(compareDirectories);

    const canMoveDirectory = (directory: IETLTaskDirectory, offset: number): boolean => {
      const siblings = siblingDirectories(directory);
      const index = siblings.findIndex(item => item.id === directory.id);
      const targetIndex = index + offset;
      return index >= 0 && targetIndex >= 0 && targetIndex < siblings.length;
    };

    /** 在同一父目录下上移 / 下移，通过重排 sortOrder 持久化顺序。 */
    const moveDirectory = async (directory: IETLTaskDirectory, offset: number) => {
      const siblings = siblingDirectories(directory);
      const index = siblings.findIndex(item => item.id === directory.id);
      const targetIndex = index + offset;
      if (index < 0 || targetIndex < 0 || targetIndex >= siblings.length) {
        return;
      }
      const reordered = siblings.slice();
      [reordered[index], reordered[targetIndex]] = [reordered[targetIndex], reordered[index]];
      try {
        await Promise.all(
          reordered
            .map((item, order) => ({ item, order }))
            .filter(({ item, order }) => (item.sortOrder ?? -1) !== order)
            .map(({ item, order }) => etlTaskDirectoryService().update({ ...item, sortOrder: order })),
        );
        await loadDirectories();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    /** 目录 ID -> 完整路径，用于表格「所属目录」列。 */
    const directoryPathMap = computed(() => {
      const byId = new Map(directories.value.filter(item => item.id).map(item => [item.id!, item]));
      const paths = new Map<string, string>();
      const resolve = (id: string, guard = new Set<string>()): string => {
        if (paths.has(id)) {
          return paths.get(id)!;
        }
        const directory = byId.get(id);
        if (!directory || guard.has(id)) {
          return '';
        }
        guard.add(id);
        const name = directory.name || '';
        const parentPath = directory.parentId ? resolve(directory.parentId, guard) : '';
        const path = parentPath ? `${parentPath} / ${name}` : name;
        paths.set(id, path);
        return path;
      };
      directories.value.forEach(directory => {
        if (directory.id) {
          resolve(directory.id);
        }
      });
      return paths;
    });

    const directoryPath = (dataApi: IDataApi): string => {
      if (dataApi.directoryId) {
        return directoryPathMap.value.get(dataApi.directoryId) || '-';
      }
      return '未分类';
    };

    const createRouteLocation = computed(() => ({
      name: 'DataApiCreate',
      query: selectedDirectoryId.value ? { directoryId: selectedDirectoryId.value } : {},
    }));

    const loadDirectories = async () => {
      directoryLoading.value = true;
      try {
        const res = await etlTaskDirectoryService().retrieve(DIRECTORY_TASK_TYPE);
        directories.value = res.data || [];
        if (selectedDirectoryId.value && !directories.value.some(item => item.id === selectedDirectoryId.value)) {
          selectedDirectoryId.value = null;
        }
      } catch (error) {
        alertService.showHttpError(error.response);
      } finally {
        directoryLoading.value = false;
      }
    };

    const clearTreeSelection = () => {
      nextTick(() => {
        if (typeof directoryTreeRef.value?.setCurrentKey === 'function') {
          directoryTreeRef.value.setCurrentKey(undefined);
        }
      });
    };

    const selectAllServices = () => {
      selectedDirectoryId.value = null;
      clearTreeSelection();
    };

    const handleDirectoryClick = (data: IDirectoryTreeNode) => {
      selectedDirectoryId.value = data.id;
    };

    const directoryDialogVisible = ref(false);
    const directoryDialogTitle = ref('新增目录');
    const directoryForm: Ref<IETLTaskDirectory> = ref(new ETLTaskDirectory());
    const editingDirectoryId: Ref<string | null> = ref(null);

    const showAddDirectoryDialog = (parentId: string | null) => {
      editingDirectoryId.value = null;
      directoryDialogTitle.value = '新增目录';
      directoryForm.value = new ETLTaskDirectory();
      directoryForm.value.parentId = parentId;
      directoryForm.value.taskType = DIRECTORY_TASK_TYPE;
      directoryForm.value.sortOrder = 0;
      directoryDialogVisible.value = true;
    };

    const showEditDirectoryDialog = (directory: IETLTaskDirectory) => {
      editingDirectoryId.value = directory.id || null;
      directoryDialogTitle.value = '重命名目录';
      directoryForm.value = { ...directory };
      directoryDialogVisible.value = true;
    };

    const saveDirectory = async () => {
      if (!directoryForm.value.name || !directoryForm.value.name.trim()) {
        alertService.showError('请输入目录名称');
        return;
      }
      directoryForm.value.taskType = DIRECTORY_TASK_TYPE;
      try {
        if (editingDirectoryId.value) {
          directoryForm.value.id = editingDirectoryId.value;
          await etlTaskDirectoryService().update(directoryForm.value);
        } else {
          await etlTaskDirectoryService().create(directoryForm.value);
        }
        directoryDialogVisible.value = false;
        alertService.showSuccess('目录保存成功');
        await loadDirectories();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const deleteDirectory = async (directory: IETLTaskDirectory) => {
      try {
        await ElMessageBox.confirm(
          `确定要删除目录「${directory.name || ''}」吗？删除后该目录下的子目录与服务将移动到未分类。`,
          '删除目录',
          {
            confirmButtonText: '删除',
            cancelButtonText: '取消',
            type: 'warning',
          },
        );
      } catch {
        return;
      }
      try {
        await etlTaskDirectoryService().delete(directory.id!);
        if (selectedDirectoryId.value === directory.id) {
          selectAllServices();
        }
        alertService.showSuccess('目录删除成功');
        await loadDirectories();
        await retrieveDataApis();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

    const moveDialogVisible = ref(false);
    const moveDataApi: Ref<IDataApi | null> = ref(null);
    const moveTargetDirectoryId: Ref<string | null> = ref(null);

    const openMoveDialog = (dataApi: IDataApi) => {
      moveDataApi.value = dataApi;
      moveTargetDirectoryId.value = dataApi.directoryId || null;
      moveDialogVisible.value = true;
    };

    const closeMoveDialog = () => {
      moveDialogVisible.value = false;
      moveDataApi.value = null;
      moveTargetDirectoryId.value = null;
    };

    const confirmMove = async () => {
      if (!moveDataApi.value?.id) {
        return;
      }
      try {
        await dataApiService().moveToDirectory(moveDataApi.value.id, moveTargetDirectoryId.value || null);
        alertService.showSuccess('数据服务已移动');
        closeMoveDialog();
        await retrieveDataApis();
      } catch (error) {
        alertService.showHttpError(error.response);
      }
    };

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
        const paginationQuery: any = {
          page: page.value - 1,
          size: itemsPerPage.value,
          sort: sort(),
          search: search.value,
        };
        if (selectedDirectoryId.value) {
          paginationQuery.directoryId = selectedDirectoryId.value;
        }
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
      await loadDirectories();
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

    const handleRowCommand = (command: string, dataApi: IDataApi) => {
      switch (command) {
        case 'move':
          openMoveDialog(dataApi);
          break;
        case 'delete':
          prepareRemove(dataApi);
          break;
        default:
          break;
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

    watch(selectedDirectoryId, async () => {
      if (page.value === 1) {
        await retrieveDataApis();
      } else {
        clear();
      }
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
      if (row.sourceType === DATA_API_SOURCE_TYPE_DATASET) {
        const paramExample = row.datasetType === 'SQL' ? 'param=value' : 'start=2026-01-01&end=2026-12-31';
        return [
          '# Header 鉴权',
          `curl "${url}?${paramExample}&pageNum=1&pageSize=1000" \\`,
          '  -H "Authorization: Bearer mcp_xxx"',
          '',
          '# Query 鉴权',
          `curl "${url}?${paramExample}&pageNum=1&pageSize=1000&access_token=mcp_xxx"`,
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
      if (type === DATA_API_SOURCE_TYPE_DATASET) {
        return '分析数据集';
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
      if (row.sourceType === DATA_API_SOURCE_TYPE_DATASET) {
        return row.datasetName || row.datasetId || '-';
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
      handleRowCommand,
      directories,
      directoryLoading,
      directoryTree,
      directoryTreeRef,
      treeProps,
      selectedDirectoryId,
      directoryPath,
      loadDirectories,
      selectAllServices,
      handleDirectoryClick,
      directoryDialogVisible,
      directoryDialogTitle,
      directoryForm,
      editingDirectoryId,
      showAddDirectoryDialog,
      showEditDirectoryDialog,
      saveDirectory,
      deleteDirectory,
      canMoveDirectory,
      moveDirectory,
      createRouteLocation,
      moveDialogVisible,
      moveDataApi,
      moveTargetDirectoryId,
      openMoveDialog,
      closeMoveDialog,
      confirmMove,
    };
  },
});
