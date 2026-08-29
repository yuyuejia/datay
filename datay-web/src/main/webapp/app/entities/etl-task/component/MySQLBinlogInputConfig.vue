<template>
  <form name="editForm" novalidate>
    <div>
      <!-- 数据源选择 -->
      <div class="form-group">
        <label for="sourceId">数据源</label>
        <DataSourceSelector
          type="source"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
      </div>

      <!-- 数据库过滤模式 -->
      <div class="form-group">
        <label for="databaseNamePattern">数据库过滤模式</label>
        <input
          type="text"
          class="form-control"
          id="databaseNamePattern"
          name="databaseNamePattern"
          v-model="formData.databaseNamePattern"
          placeholder="例如：test_db.* 或 ^prod_.*"
        />
        <small class="form-text text-muted">
          使用正则表达式过滤数据库，为空表示采集所有数据库。例如：test_db.* 表示采集test_db开头的所有数据库
        </small>
      </div>

      <!-- 表过滤模式 -->
      <div class="form-group">
        <label for="tableNamePattern">表过滤模式</label>
        <input
          type="text"
          class="form-control"
          id="tableNamePattern"
          name="tableNamePattern"
          v-model="formData.tableNamePattern"
          placeholder="例如：user_.* 或 ^order.*"
        />
        <small class="form-text text-muted"> 使用正则表达式过滤表，为空表示采集所有表。例如：user_.* 表示采集user_开头的所有表 </small>
      </div>

      <!-- Binlog文件位置 -->
      <div class="form-group">
        <label for="binlogFile">Binlog文件</label>
        <input
          type="text"
          class="form-control"
          id="binlogFile"
          name="binlogFile"
          v-model="formData.binlogFile"
          placeholder="例如：mysql-bin.000001"
        />
        <small class="form-text text-muted"> 指定从哪个Binlog文件开始采集，为空表示从当前位置开始 </small>
      </div>

      <!-- Binlog位置 -->
      <div class="form-group">
        <label for="binlogPosition">Binlog位置</label>
        <input
          type="number"
          class="form-control"
          id="binlogPosition"
          name="binlogPosition"
          v-model="formData.binlogPosition"
          placeholder="例如：107"
          min="0"
        />
        <small class="form-text text-muted"> 指定从Binlog文件的哪个位置开始采集，需要与Binlog文件一起使用 </small>
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

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

// 配置项定义
const configItems = [
  {
    key: 'sourceId',
    label: '数据源',
    defaultValue: '',
    required: true,
    description: '选择MySQL数据源作为Binlog采集源',
    show: true,
    controlType: 'SELECTDATASOURCE',
    parameterType: 'LONG',
  },
  {
    key: 'databaseNamePattern',
    label: '数据库过滤模式',
    defaultValue: '',
    required: false,
    description: '使用正则表达式过滤数据库，为空表示采集所有数据库',
    show: true,
    maxLength: 200,
    controlType: 'TEXT',
    parameterType: 'STRING',
  },
  {
    key: 'tableNamePattern',
    label: '表过滤模式',
    defaultValue: '',
    required: false,
    description: '使用正则表达式过滤表，为空表示采集所有表',
    show: true,
    maxLength: 200,
    controlType: 'TEXT',
    parameterType: 'STRING',
  },
  {
    key: 'binlogFile',
    label: 'Binlog文件',
    defaultValue: '',
    required: false,
    description: '指定从哪个Binlog文件开始采集，为空表示从当前位置开始',
    show: true,
    maxLength: 100,
    controlType: 'TEXT',
    parameterType: 'STRING',
  },
  {
    key: 'binlogPosition',
    label: 'Binlog位置',
    defaultValue: '',
    required: false,
    description: '指定从Binlog文件的哪个位置开始采集',
    show: true,
    minValue: 0,
    controlType: 'INPUTNUMBER',
    parameterType: 'LONG',
  },
];

const formRef = ref(null);
const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId || '',
  schema: props.node?.data?.config?.schema || '',
  databaseNamePattern: props.node?.data?.config?.databaseNamePattern || '',
  tableNamePattern: props.node?.data?.config?.tableNamePattern || '',
  binlogFile: props.node?.data?.config?.binlogFile || '',
  binlogPosition: props.node?.data?.config?.binlogPosition || null,
});

// 处理数据源选择事件
const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  // 验证必填项
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }

  // 如果指定了binlogPosition，必须同时指定binlogFile
  if (formData.binlogPosition && !formData.binlogFile) {
    ElMessage.error('指定Binlog位置时必须同时指定Binlog文件');
    return;
  }

  emits('save', formData);
};

const cancelConfig = () => {
  emits('cancel');
};
</script>

<style scoped>
.form-group {
  margin-bottom: 20px;
}

.form-group label {
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

.form-control:focus {
  outline: none;
  border-color: #409eff;
}

.form-text {
  display: block;
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}

.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>