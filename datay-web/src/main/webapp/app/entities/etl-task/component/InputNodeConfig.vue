<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 引入 DataSourceSelector 组件 -->
      <div class="form-group">
        <label for="sourceId">数据源</label>
        <DataSourceSelector
          type="source"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
      </div>
      <div class="form-group">
        <label for="table">表名</label>
        <!-- 使用 DataSourceTableSelector 组件 -->
        <DataSourceTableSelector
          :dataSourceId="formData.sourceId"
          :schema="formData.schema"
          :selectedTable="formData.table"
          :multiple="false"
          @selected="handleTableSelected"
        />
      </div>
      <div class="form-group">
        <label for="where">where</label>
        <input type="text" class="form-control" id="where" name="where" v-model="formData.where" />
      </div>
      <div class="form-group">
        <label for="incrColumn">增量字段</label>
        <DataSourceTableFieldSelector
          :dataSourceId="formData.sourceId"
          :schema="formData.schema"
          :table="formData.table"
          :selectedField="formData.incrColumn"
          :multiple="false"
          @selected="handleIncrColumnSelected"
        />
        <!-- <input type="text" class="form-control" id="incrColumn" name="incrColumn" v-model="formData.incrColumn" /> -->
      </div>
    </div>
    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue'; // 引入组件
import DataSourceTableFieldSelector from '@/components/DataSourceTableFieldSelector.vue'; // 引入组件

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

const configItems = [
  {
    key: 'sourceId',
    label: '数据源',
    defaultValue: '1',
    required: true,
    description: ' 选择数据源作为输入 ',
    show: true,
    controlType: 'SELECTDATASOURCE',
    parameterType: 'LONG',
  },
  {
    key: 'table',
    label: ' 表名 ',
    defaultValue: '1',
    required: true,
    description: '选择数据源表作为输入',
    show: true,
    model: false,
    controlType: 'SELECTNEWTABLE',
    parameterType: 'STRING',
  },
  {
    key: 'table_query_columns',
    label: '输出字段',
    defaultValue: '1',
    required: true,
    description: ' 为空表示选择所有列 ',
    show: true,
    controlType: 'FIELDCHECKBOX',
    parameterType: 'STRING',
    model: false,
    expression: { getDB: 'sourceId', getTable: 'table', DisplayAlias: true },
  },
  {
    key: 'where',
    label: 'where 条件 ',
    defaultValue: '1',
    required: false,
    description: '只限于 where 之后语句，不能使用中文字段',
    show: true,
    maxLength: 2000,
    rows: 2,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
  {
    key: 'ytenant0',
    label: '包含 0 租户',
    defaultValue: 'false',
    required: false,
    description: '租户运行时是否包含当前租户之后 0 租户的数据',
    show: false,
    checked: false,
    controlType: 'SWITCH',
    parameterType: 'BOOLEAN',
  },
  {
    key: 'incrColumn',
    label: '增量字段',
    defaultValue: '1',
    required: false,
    description: ' 选择增量字段作为任务保存状态 ',
    show: true,
    tableId: 0,
    model: false,
    controlType: 'FIELDRADIO',
    parameterType: 'STRING',
    expression: { getDB: 'sourceId', getTable: 'table' },
  },
  {
    key: 'maxRowsPerPartition',
    label: ' 分区批处理数 ',
    defaultValue: '20000',
    minValue: 1000,
    maxValue: 200000,
    precision: 0,
    required: true,
    description: ' 每个分区的记录数 ',
    show: true,
    controlType: 'INPUTNUMBER',
    parameterType: 'INTEGER',
  },
];

const formRef = ref(null);
const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  table: props.node?.data?.config?.table,
  where: props.node?.data?.config?.where,
  incrColumn: props.node?.data?.config?.incrColumn,
  // 其他配置项初始化
});

// 处理数据源选择事件
const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
  formData.table = ''; // 切换数据源时清空表名
};

// 处理表选择事件
const handleTableSelected = selectedTable => {
  formData.table = selectedTable?.table || '';
};

// 处理增量字段选择事件
const handleIncrColumnSelected = selectedField => {
  formData.incrColumn = selectedField?.name || '';
};

const saveConfig = async () => {
  emits('save', formData);
};

const cancelConfig = () => {
  emits('cancel');
};
</script>

<style scoped>
.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>