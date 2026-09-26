<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="生成模式">
      <el-select v-model="formData.mode" @change="handleModeChange">
            <el-option value="SEQUENCE" label="序列生成" />
            <el-option value="DATABASE" label="数据库读取" />
          </el-select>
      <small class="form-text text-muted"> SEQUENCE: 按配置生成连续序列数; DATABASE: 从数据库读取记录 </small>
    </el-form-item>

    <el-form-item v-if="formData.mode === 'SEQUENCE'" label="起始值">
      <el-input-number :controls="false"  id="startValue" name="startValue" v-model="formData.startValue" placeholder="起始值，支持参数替换如 ${param}" />
    </el-form-item>

    <el-form-item v-if="formData.mode === 'SEQUENCE'" label="生成数量">
      <el-input-number :controls="false"  id="countValue" name="countValue" v-model="formData.countValue" placeholder="生成序列的数量" />
    </el-form-item>

    <el-form-item v-if="formData.mode === 'SEQUENCE'" label="序列长度">
      <el-input-number :controls="false"  id="length" name="length" v-model="formData.length" placeholder="0表示不补零，大于0时前面补零" />
      <small class="form-text text-muted"> 例如长度为4时，1会格式化为0001 </small>
    </el-form-item>

    <el-form-item v-if="formData.mode === 'DATABASE'" label="数据源">
      <DataSourceSelector
        type="source"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
    </el-form-item>

    <el-form-item v-if="formData.mode === 'DATABASE'" label="表名">
      <el-input id="table" name="table" v-model="formData.table" placeholder="请输入表名，可选" />
      <small class="form-text text-muted"> 若不填SQL，则使用 SELECT * FROM 表名 </small>
    </el-form-item>

    <el-form-item v-if="formData.mode === 'DATABASE'" label="SQL语句">
      <el-input type="textarea" id="sql" name="sql" v-model="formData.sql" :rows="4" placeholder="请输入SQL语句，可选" />
    </el-form-item>

    <el-form-item label="数据格式">
      <el-select v-model="formData.dataFormat">
            <el-option value="TEXT" label="文本格式 (TEXT)" />
            <el-option value="JSON_ARRAY" label="JSON数组格式" />
          </el-select>
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
  mode: props.node?.data?.config?.mode || 'SEQUENCE',
  startValue: props.node?.data?.config?.startValue || '1',
  countValue: props.node?.data?.config?.countValue || '1',
  length: props.node?.data?.config?.length || 0,
  dataFormat: props.node?.data?.config?.dataFormat || 'TEXT',
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema,
  sql: props.node?.data?.config?.sql || '',
  table: props.node?.data?.config?.table || '',
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const handleModeChange = () => {};

const saveConfig = async () => {
  if (formData.mode === 'SEQUENCE') {
    if (!formData.startValue) {
      ElMessage.error('请输入起始值');
      return;
    }
    if (!formData.countValue) {
      ElMessage.error('请输入生成数量');
      return;
    }
  } else if (formData.mode === 'DATABASE') {
    if (!formData.sourceId) {
      ElMessage.error('请选择数据源');
      return;
    }
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
  min-height: 80px;
}
</style>