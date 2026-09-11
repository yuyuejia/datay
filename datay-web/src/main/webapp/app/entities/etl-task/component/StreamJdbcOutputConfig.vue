<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 数据源选择 -->
      <div class="form-group">
        <label for="sourceId">数据源</label>
        <DataSourceSelector
          type="target"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
      </div>

      <!-- 表名选择 -->
      <div class="form-group">
        <label for="table">表名</label>
        <DataSourceTableSelector
          :dataSourceId="formData.sourceId"
          :schema="formData.schema"
          :selectedTable="formData.table"
          :multiple="false"
          @selected="handleTableSelected"
          @input="handleTableInput"
        />
      </div>

      <!-- 写入策略 -->
      <div class="form-group">
        <label for="model">写入策略</label>
        <select class="form-control" v-model="formData.model">
          <option v-for="item in writeModeOptions" :key="item.value" :value="item.value">
            {{ item.name }}
          </option>
        </select>
      </div>

      <!-- 更新字段 -->
      <div class="form-group">
        <label for="updateColumn">更新字段</label>
        <textarea class="form-control" id="updateColumn" name="updateColumn" v-model="formData.updateColumn"></textarea>
      </div>

      <!-- 批处理数 -->
      <div class="form-group">
        <label for="maxRows">批处理数</label>
        <input class="form-control" v-model="formData.maxRows" type="number" controls-position="right" />
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
import DataSourceTableSelector from '@/components/DataSourceTableSelector.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 写入策略选项
const writeModeOptions = [
  {
    name: '覆盖写入（OVERWRITE）',
    value: 'overwrite',
    description: '清空目标表后写入新数据',
  },
  {
    name: '追加写入（INSERT）',
    value: 'append',
    description: '将数据追加到目标表中',
  },

  {
    name: '更新写入（UPDATE）',
    value: 'update',
    description: '根据主键更新目标表中的数据',
  },
  {
    name: '自动推断',
    value: 'auto',
    description: '根据数据流事件类型推断写入策略',
  },
];

const configItems = [
  {
    key: 'sourceId',
    label: '数据源',
    defaultValue: '1',
    required: true,
    description: ' 选择数据源作为输出',
    show: true,
    controlType: 'SELECTDATASOURCE',
    parameterType: 'LONG',
  },
  {
    key: 'table',
    label: ' 表名 ',
    defaultValue: '1',
    required: true,
    description: '目标表',
    show: true,
    model: false,
    controlType: 'SELECTNEWTABLE',
    parameterType: 'STRING',
  },
  {
    key: 'columnsMap',
    label: '输出字段',
    defaultValue: '1',
    required: true,
    description: ' 为空表示选择所有列 ',
    show: true,
    controlType: 'FIELDCHECKBOX',
    parameterType: 'STRING',
    model: false,
  },
  {
    key: 'model',
    label: '写入策略',
    defaultValue: 'append',
    required: false,
    description: '写入策略',
    show: true,
    controlType: 'SELECT',
    parameterType: 'STRING',
  },
  {
    key: 'updateColumn',
    label: '更新字段',
    defaultValue: '1',
    required: false,
    description: ' 选择更新字段 ',
    show: true,
    tableId: 0,
    model: false,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
  {
    key: 'maxRows',
    label: ' 批处理数 ',
    defaultValue: '20000',
    minValue: 1000,
    maxValue: 200000,
    precision: 0,
    required: true,
    description: '每次处理的记录数',
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
  columnsMap: props.node?.data?.config?.columnsMap,
  model: props.node?.data?.config?.model || 'overwrite',
  updateColumn: props.node?.data?.config?.updateColumn,
  maxRows: props.node?.data?.config?.maxRows || 20000,
});

// 处理数据源选择事件
const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
  formData.table = ''; // 切换数据源时清空表名
  formData.columnsMap = ''; // 清空字段选择
};

// 处理表选择事件
const handleTableSelected = selectedTable => {
  formData.table = selectedTable?.table || '';
  formData.columnsMap = ''; // 切换表时清空字段选择
};
// 处理手动输入事件
const handleTableInput = selectedTable => {
  formData.table = selectedTable || '';
};

// 处理字段选择事件
const handleColumnsSelected = selectedFields => {
  formData.columnsMap = selectedFields || '';
};

const saveConfig = async () => {
  emits('save', formData);
};

const cancelConfig = () => {
  emits('cancel');
};
</script>

<style scoped>
.form-group {
  margin-bottom: 15px;
}

label {
  display: block;
  margin-bottom: 5px;
  font-weight: bold;
}

.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}

textarea.form-control {
  resize: vertical;
  min-height: 60px;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>