<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="目标数据源">
      <DataSourceSelector
        type="target"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
    </el-form-item>
    <el-form-item label="目标表名">
      <el-input id="table" name="table" v-model="formData.table" placeholder="请输入目标表名" required />
    </el-form-item>
    <el-form-item label="Schema">
      <el-input id="schema" name="schema" v-model="formData.schema" placeholder="请输入Schema名称" />
    </el-form-item>
    <el-form-item label="写入模式">
      <el-select v-model="formData.model">
            <el-option value="append" label="追加写入 (append)" />
            <el-option value="overwrite" label="覆盖写入 (overwrite)" />
            <el-option value="update" label="更新写入 (update)" />
          </el-select>
      <small class="form-text text-muted"> append: 追加数据; overwrite: 清空后写入; update: 根据主键更新 </small>
    </el-form-item>
    <el-form-item label="字段映射">
      <div class="mapping-container">
        <div v-for="(mapping, index) in formData.header_map" :key="index" class="mapping-row">
          <el-input v-model="mapping.source" placeholder="源字段" />
          <span class="mapping-arrow">→</span>
          <el-input v-model="mapping.target" placeholder="目标字段" />
          <el-button type="danger" link size="small" @click="removeMapping(index)">删除</el-button>
        </div>
        <el-button type="primary" size="small" @click="addMapping">添加映射</el-button>
      </div>
      <small class="form-text text-muted"> 配置源字段到目标字段的映射关系，留空则按字段名自动映射 </small>
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

const mappings = ref(props.node?.data?.config?.header_map || []);

const formData = reactive({
  sourceId: props.node?.data?.config?.sourceId,
  schema: props.node?.data?.config?.schema || '',
  table: props.node?.data?.config?.table || '',
  model: props.node?.data?.config?.model || 'append',
  header_map: mappings.value,
});

const handleDataSourceSelected = selectedData => {
  formData.sourceId = selectedData.dataSourceId;
  formData.schema = selectedData.schema;
};

const addMapping = () => {
  formData.header_map.push({ source: '', target: '' });
};

const removeMapping = (index) => {
  formData.header_map.splice(index, 1);
};

const saveConfig = async () => {
  if (!formData.sourceId) {
    ElMessage.error('请选择目标数据源');
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
.mapping-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  background-color: #fafafa;
}
.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.mapping-row .form-control {
  flex: 1;
}
.mapping-arrow {
  color: #909399;
  font-weight: bold;
}
.btn-remove {
  padding: 4px 10px;
  border: 1px solid #f56c6c;
  background: #fff;
  color: #f56c6c;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.btn-remove:hover {
  background: #f56c6c;
  color: #fff;
}
</style>
