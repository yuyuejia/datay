<template>
  <el-form name="editForm" label-position="top">
    <div>
      <!-- 数据源选择（仅限 MySQL） -->
      <el-form-item label="数据源">
      <DataSourceSelector
          data-source-type="MYSQL"
          :datasourceId="formData.sourceId"
          :schema="formData.schema"
          @selected="handleDataSourceSelected"
        />
    </el-form-item>

      <!-- 数据库过滤模式 -->
      <el-form-item label="数据库过滤模式">
      <el-input id="databaseNamePattern" name="databaseNamePattern" v-model="formData.databaseNamePattern" placeholder="例如：test_db.* 或 ^prod_.*" />
        <small class="form-text text-muted">
          使用正则表达式过滤数据库，为空表示采集所有数据库。例如：test_db.* 表示采集test_db开头的所有数据库
        </small>
    </el-form-item>

      <!-- 表过滤模式 -->
      <el-form-item label="表过滤模式">
      <el-input id="tableNamePattern" name="tableNamePattern" v-model="formData.tableNamePattern" placeholder="例如：user_.* 或 ^order.*" />
        <small class="form-text text-muted"> 使用正则表达式过滤表，为空表示采集所有表。例如：user_.* 表示采集user_开头的所有表 </small>
    </el-form-item>

      <!-- 从库 server-id -->
      <el-form-item label="server-id">
      <el-input-number :controls="false" id="serverId" name="serverId" v-model="formData.serverId" :min="1" :max="4294967295" placeholder="默认 1000" />
        <small class="form-text text-muted"> 伪装成从库的唯一标识，默认 1000。多个任务连接同一 MySQL 实例时需保证 server-id 唯一，避免冲突 </small>
    </el-form-item>

      <!-- Binlog文件位置 -->
      <el-form-item label="Binlog文件">
      <el-input id="binlogFile" name="binlogFile" v-model="formData.binlogFile" placeholder="例如：mysql-bin.000001" />
        <small class="form-text text-muted"> 指定从哪个Binlog文件开始采集，为空表示从当前位置开始 </small>
    </el-form-item>

      <!-- Binlog位置 -->
      <el-form-item label="Binlog位置">
      <el-input-number :controls="false"  id="binlogPosition" name="binlogPosition" v-model="formData.binlogPosition" placeholder="例如：107" :min="0" />
        <small class="form-text text-muted"> 指定从Binlog文件的哪个位置开始采集，需要与Binlog文件一起使用 </small>
    </el-form-item>

      <!-- 历史全量快照 -->
      <el-form-item label="首次读取历史全量数据">
      <el-switch id="snapshot" name="snapshot" v-model="formData.snapshot" />
        <small class="form-text text-muted"> 启用后，首次运行（无断点）会先全量读取匹配表的数据，再启动增量同步；任务重启有断点时不重复全量 </small>
    </el-form-item>

      <el-form-item v-if="formData.snapshot" label="快照分批大小">
      <el-input-number :controls="false" id="snapshotFetchSize" name="snapshotFetchSize" v-model="formData.snapshotFetchSize" :min="1" />
        <small class="form-text text-muted"> 每批下发的记录数，默认 10000 </small>
    </el-form-item>
    </div>
  </el-form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

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
    key: 'serverId',
    label: 'server-id',
    defaultValue: 1000,
    required: false,
    description: '伪装成从库的唯一标识，默认 1000，需确保与其它从库不冲突',
    show: true,
    minValue: 1,
    maxValue: 4294967295,
    controlType: 'INPUTNUMBER',
    parameterType: 'LONG',
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
  serverId: props.node?.data?.config?.serverId || 1000,
  binlogFile: props.node?.data?.config?.binlogFile || '',
  binlogPosition: props.node?.data?.config?.binlogPosition || null,
  snapshot: props.node?.data?.config?.snapshot || false,
  snapshotFetchSize: props.node?.data?.config?.snapshotFetchSize || 10000,
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

defineExpose({ saveConfig });
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