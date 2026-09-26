<template>
  <el-form name="editForm" label-position="top">
    <el-form-item label="应用 App ID">
      <el-input id="appId" name="appId" v-model="formData.appId" placeholder="如 cli_xxxxxxxx" required />
    </el-form-item>
    <el-form-item label="应用 App Secret">
      <el-input type="password" id="appSecret" name="appSecret" v-model="formData.appSecret" placeholder="应用密钥" required />
    </el-form-item>
    <el-form-item label="多维表格标识 (appToken)">
      <el-input id="appToken" name="appToken" v-model="formData.appToken" placeholder="多维表格 URL 中 /base/ 后面的部分" required />
    </el-form-item>
    <el-form-item label="数据表标识 (tableId)">
      <el-input id="tableId" name="tableId" v-model="formData.tableId" placeholder="URL 中 table= 参数的值" required />
    </el-form-item>
    <el-form-item label="单批写入条数">
      <el-input-number :controls="false"  id="batchSize" name="batchSize" v-model="formData.batchSize" ::min="1" ::max="500" />
    </el-form-item>
    <el-form-item label="记录 ID 字段名">
      <el-input id="recordIdField" name="recordIdField" v-model="formData.recordIdField" placeholder="默认 record_id，存在则更新否则新增" />
    </el-form-item>
    <el-form-item label="排除字段（逗号分隔）">
      <el-input id="excludeFields" name="excludeFields" v-model="formData.excludeFields" placeholder="如 created_time,last_modified_time" />
    </el-form-item>
    <el-form-item label="开放平台地址">
      <el-input id="baseUrl" name="baseUrl" v-model="formData.baseUrl" placeholder="默认 https://open.feishu.cn" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const cfg = props.node?.data?.config || {};

const formData = reactive({
  appId: cfg.appId || '',
  appSecret: cfg.appSecret || '',
  appToken: cfg.appToken || '',
  tableId: cfg.tableId || '',
  batchSize: cfg.batchSize || 500,
  recordIdField: cfg.recordIdField || 'record_id',
  excludeFields: cfg.excludeFields || '',
  baseUrl: cfg.baseUrl || 'https://open.feishu.cn',
});

const saveConfig = () => {
  if (!formData.appId.trim()) {
    ElMessage.error('请输入应用 App ID');
    return;
  }
  if (!formData.appSecret.trim()) {
    ElMessage.error('请输入应用 App Secret');
    return;
  }
  if (!formData.appToken.trim()) {
    ElMessage.error('请输入多维表格标识 appToken');
    return;
  }
  if (!formData.tableId.trim()) {
    ElMessage.error('请输入数据表标识 tableId');
    return;
  }
  emits('save', { ...formData });
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
.form-actions {
  margin-top: 30px;
  text-align: right;
}
</style>
