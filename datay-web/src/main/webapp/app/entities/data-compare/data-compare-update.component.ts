import { computed, defineComponent, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DataCompareService, { type IDataCompareTable, type IDataCompareTask } from './data-compare.service';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';

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
      handleLeftSource,
      handleRightSource,
      handleSelectedTables,
      removeTable,
      save,
      cancel,
    };
  },
});
