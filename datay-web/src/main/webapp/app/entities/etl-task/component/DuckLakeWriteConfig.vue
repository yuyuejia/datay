<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label for="sourceId">DuckLake数据源</label>
      <DataSourceSelector
        type="target"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择配置了S3信息的DuckLake数据源 </small>
    </div>
    <div class="form-group">
      <label for="table">目标表名</label>
      <input type="text" class="form-control" id="table" name="table" v-model="formData.table" placeholder="请输入DuckLake表名" required />
    </div>
    <div class="form-group">
      <label for="schema">Schema</label>
      <input type="text" class="form-control" id="schema" name="schema" v-model="formData.schema" placeholder="请输入Schema名称" />
    </div>
    <div class="form-group">
      <label for="model">写入模式</label>
      <select class="form-control" v-model="formData.model">
        <option value="auto">自动推断</option>
        <option value="INSERT">追加写入</option>
        <option value="OVERWRITE">覆盖写入</option>
        <option value="UPDATE">更新写入</option>
        <option value="DELETE">删除</option>
        <option value="QUERY">查询/DDL</option>
      </select>
      <small class="form-text text-muted"> auto模式将根据上游事件类型自动判断 </small>
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
  table: props.node?.data?.config?.table || '',
  model: props.node?.data?.config?.model || 'auto',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择DuckLake数据源');
    return;
  }
  if (!formData.table.trim()) {
    ElMessage.error('请输入目标表名');
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
</style>