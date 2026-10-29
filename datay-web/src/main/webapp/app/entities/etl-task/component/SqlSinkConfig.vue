<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="目标数据源">
      <DataSourceSelector type="target" :datasource-id="formData.sourceId" :schema="formData.schema" @selected="handleDataSourceSelected" />
      <small class="form-text text-muted"> 选择 SQL 要写入的目标关系型数据库 </small>
    </el-form-item>

    <el-form-item>
      <template #label>SQL 模板 <DynamicParameterHelp /></template>
      <el-input
        id="sql"
        name="sql"
        v-model="formData.sql"
        type="textarea"
        :rows="10"
        placeholder="INSERT INTO dws_store_sales (stat_date, store_key, order_cnt, sales_amount) VALUES (${stat_date}, ${store_key}, ${order_cnt_delta}, ${sales_amount_delta}) ON DUPLICATE KEY UPDATE order_cnt = order_cnt + VALUES(order_cnt), sales_amount = sales_amount + VALUES(sales_amount)"
      />
      <small class="form-text text-muted">
        只支持单条 DML（INSERT / UPDATE / DELETE / MERGE）。参数会转换为 JDBC 占位符并绑定：<code>${字段名}</code>、
        <code>${attr:属性名}</code>、<code>${FLOW_FILE_DATA}</code>、<code>${ROW_DATA}</code>、<code>#{内置参数}</code>。
        字符串和日期不需要加引号，也不要手动在占位符外加引号。动态参数只能用于值位置，不能用于表名或列名。
      </small>
    </el-form-item>

    <el-form-item label="批量大小">
      <el-input-number v-model="formData.batchSize" :min="1" :max="10000" :step="100" />
      <small class="form-text text-muted"> 每个 JDBC 批次的行数，默认 500 </small>
    </el-form-item>

    <el-form-item label="事务">
      <el-switch v-model="formData.transaction" />
      <small class="form-text text-muted"> 开启后一个 FlowFile 内的所有批次在同一事务中提交；Doris 等不支持事务的目标请关闭。 </small>
    </el-form-item>

    <el-form-item label="忽略错误">
      <el-switch v-model="formData.ignoreError" />
      <small class="form-text text-muted"> 开启后执行失败只记录日志并跳过，默认关闭 </small>
    </el-form-item>

    <el-collapse>
      <el-collapse-item title="高级配置" name="advanced">
        <el-form-item label="事件类型过滤">
          <el-select
            v-model="formData.eventTypes"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="不配置表示处理所有事件"
          >
            <el-option label="INSERT" value="INSERT" />
            <el-option label="UPDATE" value="UPDATE" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
          <small class="form-text text-muted"> 只处理指定事件类型；FlowFile 需带 _eventType 属性 </small>
        </el-form-item>

        <el-form-item label="查询超时（秒）">
          <el-input-number v-model="formData.queryTimeout" :min="0" :max="3600" :step="10" />
          <small class="form-text text-muted"> 0 表示不设置超时 </small>
        </el-form-item>
      </el-collapse-item>
    </el-collapse>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DynamicParameterHelp from '../DynamicParameterHelp.vue';

const props = defineProps({
  node: {
    type: Object,
    default: () => ({}),
  },
});

const emits = defineEmits(['save']);

const normalizeStringList = raw => {
  if (Array.isArray(raw)) {
    return raw.map(item => String(item ?? ''));
  }
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item ?? ''));
      }
    } catch {
      return raw
        .split(',')
        .map(item => item.trim())
        .filter(Boolean);
    }
  }
  return [];
};

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema || '',
  sql: props.node?.data?.config?.sql || '',
  batchSize: props.node?.data?.config?.batchSize || 500,
  transaction: props.node?.data?.config?.transaction !== false,
  ignoreError: props.node?.data?.config?.ignoreError === true,
  eventTypes: normalizeStringList(props.node?.data?.config?.eventTypes),
  queryTimeout: props.node?.data?.config?.queryTimeout || 0,
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择目标数据源');
    return;
  }
  const sqlText = (formData.sql || '').trim();
  if (!sqlText) {
    ElMessage.error('请输入 SQL 模板');
    return;
  }

  if (!formData.batchSize || formData.batchSize < 1 || formData.batchSize > 10000) {
    ElMessage.error('批量大小应在 1-10000 之间');
    return;
  }
  if (formData.queryTimeout < 0 || formData.queryTimeout > 3600) {
    ElMessage.error('查询超时应为 0-3600 秒');
    return;
  }

  emits('save', {
    ...formData,
    sql: sqlText,
    eventTypes: (formData.eventTypes || []).map(type => String(type || '').trim()).filter(Boolean),
  });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.7;
}
</style>
