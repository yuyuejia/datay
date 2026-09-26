<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="Doris数据源">
      <DataSourceSelector
        type="target"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择Doris数据库数据源 </small>
    </el-form-item>
    <el-form-item label="目标表名">
      <el-input id="table" name="table" v-model="formData.table" placeholder="请输入Doris表名" required />
    </el-form-item>
    <el-form-item label="Schema">
      <el-input id="schema" name="schema" v-model="formData.schema" placeholder="请输入Schema名称" />
    </el-form-item>
    <el-form-item label="写入模式">
      <el-select v-model="formData.model">
            <el-option value="INSERT" label="INSERT" />
            <el-option value="DELETE" label="DELETE" />
          </el-select>
      <small class="form-text text-muted"> Doris StreamLoad支持的写入模式 </small>
    </el-form-item>
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

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  table: props.node?.data?.config?.table || '',
  model: props.node?.data?.config?.model || 'INSERT',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择Doris数据源');
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