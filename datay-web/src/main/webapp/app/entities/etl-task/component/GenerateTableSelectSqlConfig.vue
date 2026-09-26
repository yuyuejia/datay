<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="数据源">
      <DataSourceSelector
        type="source"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
    </el-form-item>
    <el-form-item label="Schema">
      <el-input id="schema" name="schema" v-model="formData.schema" placeholder="请输入Schema名称" required />
    </el-form-item>
    <el-form-item label="指定表（可选）">
      <el-input id="table" name="table" v-model="formData.table" placeholder="多个表用逗号分隔，留空则处理Schema下所有表" />
      <small class="form-text text-muted"> 指定需要处理的表，多个表用英文逗号分隔 </small>
    </el-form-item>
    <el-form-item label="启用分区">
      <el-switch v-model="formData.enablePartition" />
      <small class="form-text text-muted"> 根据主键字段对大数据量表进行分区查询 </small>
    </el-form-item>
    <el-form-item v-if="formData.enablePartition" label="分区数量">
      <el-input-number :controls="false"  id="partitionCount" name="partitionCount" v-model="formData.partitionCount" ::min="1" ::max="256" />
      <small class="form-text text-muted"> 大数据量表的分区查询数量，默认4 </small>
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
  schema: props.node?.data?.config?.schema || '',
  table: props.node?.data?.config?.table || '',
  enablePartition: props.node?.data?.config?.enablePartition || false,
  partitionCount: props.node?.data?.config?.partitionCount || 4,
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
  if (!formData.schema.trim()) {
    ElMessage.error('请输入Schema名称');
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