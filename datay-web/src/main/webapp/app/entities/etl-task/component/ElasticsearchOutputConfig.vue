<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="目标数据源">
      <DataSourceSelector
        type="target"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        dataSourceType="ELASTICSEARCH"
        @selected="handleDataSourceSelected"
      />
      <small class="form-text text-muted"> 选择 Elasticsearch 数据源作为输出目标 </small>
    </el-form-item>
    <el-form-item label="目标索引">
      <el-input id="table" name="table" v-model="formData.table" placeholder="例如：my_index，留空则使用上游表名" />
      <small class="form-text text-muted"> 写入的 Elasticsearch 索引名，索引不存在时自动创建 </small>
    </el-form-item>
    <el-form-item label="写入模式">
      <el-select v-model="formData.model">
        <el-option value="append" label="追加写入 (append)" />
        <el-option value="overwrite" label="覆盖写入 (overwrite)" />
      </el-select>
      <small class="form-text text-muted"> append: 追加数据; overwrite: 首批写入前重建索引 </small>
    </el-form-item>
    <el-form-item label="文档 ID 字段">
      <el-input id="idField" name="idField" v-model="formData.idField" placeholder="可选，用作文档 _id 的字段名" />
      <small class="form-text text-muted"> 留空则由 Elasticsearch 自动生成文档 _id，同一字段值重复写入会覆盖已有文档 </small>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
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
  model: props.node?.data?.config?.model || 'append',
  idField: props.node?.data?.config?.idField || '',
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
  emits('save', formData);
};

defineExpose({ saveConfig });
</script>

<style scoped>
.form-text {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
