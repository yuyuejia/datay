import { computed, defineComponent, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DataCompareService, { type IDataCompareTable, type IDataCompareTask } from './data-compare.service';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';
import DataSourceService from '@/entities/data-source/data-source.service';

interface CompareForm {
  id?: string;
  taskName: string;
  taskDesc: string;
  leftDataSourceId: string;
  leftSchema: string;
  rightDataSourceId: string;
  rightSchema: string;
  compareRecordCount: boolean;
  compareDetail: boolean;
  ignoreCase: boolean;
  trimWhitespace: boolean;
  outputFormat: string;
  outputDir: string;
  tables: IDataCompareTable[];
}

export default defineComponent({
  name: 'DataCompareEdit',
  components: {
    DataSourceSelector,
    DataSourceTableSelector,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const service = new DataCompareService();

    const isEdit = computed(() => Boolean(route.params.id));
    const formReady = ref(!isEdit.value);
    const isSaving = ref(false);

    const form = reactive<CompareForm>({
      taskName: '',
      taskDesc: '',
      leftDataSourceId: '',
      leftSchema: '',
      rightDataSourceId: '',
      rightSchema: '',
      compareRecordCount: true,
      compareDetail: false,
      ignoreCase: false,
      trimWhitespace: false,
      outputFormat: 'CSV',
      outputDir: 'data-compare',
      tables: [],
    });

    const selectorTables = computed(() => form.tables.map(table => ({ table: table.leftTable })));

    // 右侧表下拉选项
    const dataSourceService = new DataSourceService();
    const rightTableOptions = ref<string[]>([]);
    const rightTableLoading = ref(false);
    let rightTableRequestId = 0;

    const loadRightTables = async () => {
      const dataSourceId = form.rightDataSourceId;
      const schema = form.rightSchema;
      const requestId = ++rightTableRequestId;

      if (!dataSourceId || !schema) {
        rightTableOptions.value = [];
        rightTableLoading.value = false;
        return;
      }

      rightTableLoading.value = true;
      try {
        const res = await dataSourceService.getTables(dataSourceId, schema, 1000);
        if (requestId !== rightTableRequestId) {
          return;
        }
        rightTableOptions.value = (res.data || []).filter((item: any) => item?.table).map((item: any) => String(item.table));
      } catch (error) {
        if (requestId !== rightTableRequestId) {
          return;
        }
        rightTableOptions.value = [];
        console.error('获取右侧表清单失败', error);
        ElMessage.warning('获取右侧表清单失败');
      } finally {
        if (requestId === rightTableRequestId) {
          rightTableLoading.value = false;
        }
      }
    };

    watch(() => [form.rightDataSourceId, form.rightSchema], loadRightTables);

    // 左侧表字段下拉选项：key 为左侧表名，value 为字段名列表
    const leftTableFields = ref<Record<string, string[]>>({});
    const leftTablePrimaryKeys = ref<Record<string, string[]>>({});
    const leftFieldsLoading = ref<Record<string, boolean>>({});
    const leftFieldRequestIds = new Map<string, number>();

    // 未配置主键时，默认填充左侧表主键
    const applyDefaultKeyColumns = (row: IDataCompareTable) => {
      if (!row.leftTable || row.keyColumns?.trim()) {
        return;
      }
      const primaryKeys = leftTablePrimaryKeys.value[row.leftTable];
      if (primaryKeys?.length) {
        row.keyColumns = primaryKeys.join(',');
      }
    };

    const loadLeftTableFields = async (table: string) => {
      if (!form.leftDataSourceId || !form.leftSchema || !table) {
        return;
      }

      const requestId = (leftFieldRequestIds.get(table) || 0) + 1;
      leftFieldRequestIds.set(table, requestId);
      leftFieldsLoading.value = { ...leftFieldsLoading.value, [table]: true };

      try {
        const res = await dataSourceService.getFields(form.leftDataSourceId, form.leftSchema, table);
        if (leftFieldRequestIds.get(table) !== requestId) {
          return;
        }
        const fields = (res.data || []).filter((field: any) => field?.name);
        leftTableFields.value = {
          ...leftTableFields.value,
          [table]: fields.map((field: any) => String(field.name)),
        };

        // 默认使用左表主键；仅在当前行尚未配置主键时填充，避免覆盖已保存的配置
        const primaryKeys = fields.filter((field: any) => field?.primaryKey ?? field?.isPrimaryKey).map((field: any) => String(field.name));
        leftTablePrimaryKeys.value = { ...leftTablePrimaryKeys.value, [table]: primaryKeys };
        form.tables.filter(item => item.leftTable === table).forEach(applyDefaultKeyColumns);
      } catch (error) {
        if (leftFieldRequestIds.get(table) !== requestId) {
          return;
        }
        leftTableFields.value = { ...leftTableFields.value, [table]: [] };
        console.error(`获取左侧表 ${table} 字段失败`, error);
      } finally {
        if (leftFieldRequestIds.get(table) === requestId) {
          leftFieldsLoading.value = { ...leftFieldsLoading.value, [table]: false };
        }
      }
    };

    // 左侧数据源或 Schema 变化时，清空旧字段并重新加载
    watch(
      () => [form.leftDataSourceId, form.leftSchema],
      () => {
        leftTableFields.value = {};
        leftTablePrimaryKeys.value = {};
        leftFieldsLoading.value = {};
        leftFieldRequestIds.clear();
        form.tables.forEach(table => loadLeftTableFields(table.leftTable));
      },
    );

    // 左侧比对表变化时，为新增的表加载字段
    watch(
      () => form.tables.map(table => table.leftTable).join('\u0000'),
      () => {
        form.tables.forEach(table => {
          if (!table.leftTable) {
            return;
          }
          if (leftTableFields.value[table.leftTable]) {
            applyDefaultKeyColumns(table);
          } else if (!leftFieldsLoading.value[table.leftTable]) {
            loadLeftTableFields(table.leftTable);
          }
        });
      },
    );

    const splitColumns = (value?: string): string[] =>
      (value || '')
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);

    const updateColumns = (row: IDataCompareTable, field: 'keyColumns' | 'compareColumns', values: string[]) => {
      row[field] = (values || [])
        .map(value => String(value).trim())
        .filter(Boolean)
        .join(',');
    };

    const handleLeftSource = (selected: { dataSourceId: string; schema: string }) => {
      form.leftDataSourceId = selected.dataSourceId;
      form.leftSchema = selected.schema;
    };

    const handleRightSource = (selected: { dataSourceId: string; schema: string }) => {
      form.rightDataSourceId = selected.dataSourceId;
      form.rightSchema = selected.schema;
    };

    const handleSelectedTables = (tables: any[]) => {
      const existing = new Map(form.tables.map(table => [table.leftTable, table]));
      form.tables = (tables || []).map(item => {
        const leftTable = item.table || item.leftTable;
        const old = existing.get(leftTable);
        return old
          ? old
          : {
              leftTable,
              rightTable: leftTable,
              leftWhere: '',
              rightWhere: '',
              keyColumns: '',
              compareColumns: '',
            };
      });
    };

    const removeTable = (index: number) => {
      form.tables.splice(index, 1);
    };

    const buildTablesFromLegacy = (task: any): IDataCompareTable[] => {
      if (task.tables?.length) {
        return task.tables;
      }
      if (task.leftTable) {
        return [
          {
            leftTable: task.leftTable,
            rightTable: task.rightTable || task.leftTable,
            leftWhere: task.leftWhere || '',
            rightWhere: task.rightWhere || '',
            keyColumns: task.keyColumns || '',
            compareColumns: task.compareColumns || '',
          },
        ];
      }
      return [];
    };

    const loadTask = async () => {
      if (!route.params.id) return;
      try {
        const task = await service.find(String(route.params.id));
        form.id = task.id;
        form.taskName = task.taskName || '';
        form.taskDesc = task.taskDesc || '';
        form.leftDataSourceId = task.leftDataSourceId || '';
        form.leftSchema = task.leftSchema || '';
        form.rightDataSourceId = task.rightDataSourceId || '';
        form.rightSchema = task.rightSchema || '';
        form.compareRecordCount = task.compareRecordCount !== false;
        form.compareDetail = task.compareDetail === true;
        form.ignoreCase = Boolean(task.ignoreCase);
        form.trimWhitespace = Boolean(task.trimWhitespace);
        form.outputFormat = task.outputFormat || 'CSV';
        form.outputDir = task.outputDir || 'data-compare';
        form.tables = buildTablesFromLegacy(task);
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.message || '加载任务失败');
      } finally {
        formReady.value = true;
      }
    };

    const validate = () => {
      if (!form.taskName.trim()) return '请输入任务名称';
      if (!form.leftDataSourceId) return '请选择左侧数据源';
      if (!form.rightDataSourceId) return '请选择右侧数据源';
      if (!form.compareRecordCount && !form.compareDetail) return '请至少选择一个对比项';
      if (form.tables.length === 0) return '请至少选择一张比对表';
      for (const table of form.tables) {
        if (!table.leftTable?.trim()) return '存在未选择左侧表的数据';
        if (!table.rightTable?.trim()) return `请输入表 ${table.leftTable} 的右侧表名`;
        if (form.compareDetail && !table.keyColumns?.trim()) return `请输入表 ${table.leftTable} 的主键字段`;
      }
      return '';
    };

    const save = async () => {
      const error = validate();
      if (error) {
        ElMessage.warning(error);
        return;
      }
      isSaving.value = true;
      try {
        const payload: IDataCompareTask = {
          id: form.id,
          taskName: form.taskName.trim(),
          taskDesc: form.taskDesc,
          leftDataSourceId: form.leftDataSourceId,
          leftSchema: form.leftSchema,
          rightDataSourceId: form.rightDataSourceId,
          rightSchema: form.rightSchema,
          compareRecordCount: form.compareRecordCount,
          compareDetail: form.compareDetail,
          ignoreCase: form.ignoreCase,
          trimWhitespace: form.trimWhitespace,
          outputFormat: form.outputFormat,
          outputDir: form.outputDir,
          tables: form.tables.map(table => ({
            leftTable: table.leftTable?.trim(),
            rightTable: table.rightTable?.trim() || table.leftTable?.trim(),
            leftWhere: table.leftWhere,
            rightWhere: table.rightWhere,
            keyColumns: table.keyColumns,
            compareColumns: table.compareColumns,
          })),
        };
        if (isEdit.value) {
          await service.update(payload);
          ElMessage.success('保存成功');
        } else {
          await service.create(payload);
          ElMessage.success('创建成功');
        }
        router.push({ name: 'DataCompare' });
      } catch (e: any) {
        ElMessage.error(e?.response?.data?.detail || e?.response?.data?.message || e?.message || '保存失败');
      } finally {
        isSaving.value = false;
      }
    };

    const cancel = () => router.push({ name: 'DataCompare' });

    onMounted(loadTask);

    return {
      isEdit,
      formReady,
      isSaving,
      form,
      selectorTables,
      rightTableOptions,
      rightTableLoading,
      leftTableFields,
      leftFieldsLoading,
      splitColumns,
      updateColumns,
      handleLeftSource,
      handleRightSource,
      handleSelectedTables,
      removeTable,
      save,
      cancel,
    };
  },
});
