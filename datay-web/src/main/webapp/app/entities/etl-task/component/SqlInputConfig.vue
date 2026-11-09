<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="数据源">
      <DataSourceSelector type="source" :datasourceId="formData.sourceId" :schema="formData.schema" @selected="handleDataSourceSelected" />
    </el-form-item>
    <el-form-item>
      <template #label>SQL语句 <DynamicParameterHelp /></template>
      <el-input
        type="textarea"
        id="sql"
        name="sql"
        v-model="formData.sql"
        :rows="8"
        :maxlength="5000"
        placeholder="请输入SQL查询语句，留空则使用上游FlowFile中的SQL"
      />
      <small class="form-text text-muted">
        支持标准SQL语法和动态参数：<code>${attr:属性名}</code> / <code>${属性名}</code> / <code>${FLOW_FILE_DATA}</code> /
        <code>#{内置参数}</code>；<code>${属性名}</code> 可引用上游属性或单行数据字段；留空时将使用上游传递的完整SQL语句
      </small>
    </el-form-item>
    <el-form-item label="输出表名">
      <el-input
        id="outputTable"
        name="outputTable"
        v-model="formData.outputTable"
        placeholder="可选，默认 sql_query_result"
      />
      <small class="form-text text-muted">
        查询结果输出给下游时携带的表名；多个 SqlInput 接入同一个 Join 时，请分别配置不同的表名，否则只会识别到一张表
      </small>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DataSourceSelector from '@/components/DataSourceSelector.vue';
import DynamicParameterHelp from '../DynamicParameterHelp.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  sql: props.node?.data?.config?.sql || '',
  outputTable: props.node?.data?.config?.outputTable || '',
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
  formData.outputTable = (formData.outputTable || '').trim();
  if (formData.outputTable && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(formData.outputTable)) {
    ElMessage.error('输出表名只能包含字母、数字和下划线，且不能以数字开头');
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
}
</style>
