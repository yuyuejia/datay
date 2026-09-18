<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label for="mode">生成模式</label>
      <select class="form-control" v-model="formData.mode" @change="handleModeChange">
        <option value="SEQUENCE">序列生成</option>
        <option value="DATABASE">数据库读取</option>
      </select>
      <small class="form-text text-muted"> SEQUENCE: 按配置生成连续序列数; DATABASE: 从数据库读取记录 </small>
    </div>

    <div v-if="formData.mode === 'SEQUENCE'" class="form-group">
      <label for="startValue">起始值</label>
      <input type="number" class="form-control" id="startValue" name="startValue" v-model="formData.startValue" placeholder="起始值，支持参数替换如 ${param}" />
    </div>

    <div v-if="formData.mode === 'SEQUENCE'" class="form-group">
      <label for="countValue">生成数量</label>
      <input type="number" class="form-control" id="countValue" name="countValue" v-model="formData.countValue" placeholder="生成序列的数量" />
    </div>

    <div v-if="formData.mode === 'SEQUENCE'" class="form-group">
      <label for="length">序列长度</label>
      <input type="number" class="form-control" id="length" name="length" v-model="formData.length" placeholder="0表示不补零，大于0时前面补零" />
      <small class="form-text text-muted"> 例如长度为4时，1会格式化为0001 </small>
    </div>

    <div v-if="formData.mode === 'DATABASE'" class="form-group">
      <label for="sourceId">数据源</label>
      <DataSourceSelector
        type="source"
        :datasourceId="formData.sourceId"
        :schema="formData.schema"
        @selected="handleDataSourceSelected"
      />
    </div>

    <div v-if="formData.mode === 'DATABASE'" class="form-group">
      <label for="table">表名</label>
      <input type="text" class="form-control" id="table" name="table" v-model="formData.table" placeholder="请输入表名，可选" />
      <small class="form-text text-muted"> 若不填SQL，则使用 SELECT * FROM 表名 </small>
    </div>

    <div v-if="formData.mode === 'DATABASE'" class="form-group">
      <label for="sql">SQL语句</label>
      <textarea class="form-control" id="sql" name="sql" v-model="formData.sql" :rows="4" placeholder="请输入SQL语句，可选"></textarea>
    </div>

    <div class="form-group">
      <label for="dataFormat">数据格式</label>
      <select class="form-control" v-model="formData.dataFormat">
        <option value="TEXT">文本格式 (TEXT)</option>
        <option value="JSON_ARRAY">JSON数组格式</option>
      </select>
    </div>

    <div class="form-actions">
      <el-button type="primary" @click="saveConfig">保存</el-button>
      <el-button @click="cancelConfig">取消</el-button>
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

const emits = defineEmits(['save', 'cancel']);

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

const cancelConfig = () => {
  emits('cancel');
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