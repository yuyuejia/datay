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
      <label for="table">源表名</label>
      <input type="text" class="form-control" id="table" name="table" v-model="formData.table" placeholder="请输入源表名，多表用逗号分隔" required />
      <small class="form-text text-muted"> 支持多表同步，多个表用英文逗号分隔 </small>
    </div>
    <div class="form-group">
      <label for="outputTable">输出表名</label>
      <input
        type="text"
        class="form-control"
        id="outputTable"
        name="outputTable"
        v-model="formData.outputTable"
        placeholder="可选，默认为源表名"
      />
      <small class="form-text text-muted"> 写入 DuckDB 的表名，下游组件通过该表名引用数据；未填写时使用源表名 </small>
    </div>
    <div class="form-group">
      <label for="incrColumn">增量字段</label>
      <input type="text" class="form-control" id="incrColumn" name="incrColumn" v-model="formData.incrColumn" placeholder="请输入增量同步字段名" />
      <small class="form-text text-muted"> 用于增量同步的字段，多表用逗号分隔对应 </small>
    </div>
    <div class="form-group">
      <label for="where">过滤条件</label>
      <textarea class="form-control" id="where" name="where" v-model="formData.where" :rows="3" placeholder="请输入WHERE条件，如 status=1"></textarea>
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
  outputTable: props.node?.data?.config?.outputTable || '',
  incrColumn: props.node?.data?.config?.incrColumn || '',
  where: props.node?.data?.config?.where || '',
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
  if (!formData.table.trim()) {
    ElMessage.error('请输入源表名');
    return;
  }
  if (formData.outputTable && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(formData.outputTable.trim())) {
    ElMessage.error('输出表名只能包含字母、数字和下划线，且不能以数字开头');
    return;
  }
  formData.outputTable = formData.outputTable ? formData.outputTable.trim() : '';
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
  min-height: 60px;
}
</style>
