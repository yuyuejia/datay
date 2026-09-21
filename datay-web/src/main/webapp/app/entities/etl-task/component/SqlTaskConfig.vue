<template>
  <form name="editForm" novalidate>
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
      <label for="sql">SQL语句</label>
      <textarea class="form-control" id="sql" name="sql" v-model="formData.sql" :rows="8" :maxlength="5000" placeholder="请输入要执行的SQL语句"></textarea>
      <small class="form-text text-muted"> 支持DDL、DML等各种SQL操作，如CREATE TABLE, INSERT, UPDATE等 </small>
    </div>
  </form>
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

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  sql: props.node?.data?.config?.sql || '',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择数据源');
    return;
  }
  if (!formData.sql.trim()) {
    ElMessage.error('请输入SQL语句');
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
.form-control {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
}
.form-control:focus {
  border-color: #409eff;
  outline: none;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.form-actions {
  margin-top: 30px;
  text-align: right;
}
textarea {
  resize: vertical;
  min-height: 120px;
  font-family: monospace;
}
</style>
