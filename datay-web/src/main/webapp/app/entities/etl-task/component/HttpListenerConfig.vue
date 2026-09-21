<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label for="port">监听端口</label>
      <input type="number" class="form-control" id="port" name="port" v-model.number="formData.port" :min="1" :max="65535" placeholder="请输入端口号" required />
    </div>
    <div class="form-group">
      <label for="host">监听地址</label>
      <input type="text" class="form-control" id="host" name="host" v-model="formData.host" placeholder="0.0.0.0" />
      <small class="form-text text-muted"> 默认0.0.0.0表示监听所有网络接口 </small>
    </div>
    <div class="form-group">
      <label for="path">请求路径</label>
      <input type="text" class="form-control" id="path" name="path" v-model="formData.path" placeholder="/" />
      <small class="form-text text-muted"> 默认路径 / </small>
    </div>
    <div class="form-group">
      <label for="enableHttps">启用HTTPS</label>
      <el-switch v-model="formData.enableHttps" />
    </div>
    <div class="form-group">
      <label for="maxBodySize">最大请求体大小 (字节)</label>
      <input type="number" class="form-control" id="maxBodySize" name="maxBodySize" v-model.number="formData.maxBodySize" :min="1024" step="1024" />
      <small class="form-text text-muted"> 默认10MB (10485760字节) </small>
    </div>
    <div class="form-group">
      <label for="dataFormat">数据格式</label>
      <select class="form-control" v-model="formData.dataFormat">
        <option value="AUTO">自动检测</option>
        <option value="JSON">JSON格式</option>
        <option value="TEXT">文本格式</option>
        <option value="XML">XML格式</option>
      </select>
    </div>
  </form>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const formData = reactive({
  port: props.node?.data?.config?.port || 8080,
  host: props.node?.data?.config?.host || '0.0.0.0',
  path: props.node?.data?.config?.path || '/',
  enableHttps: props.node?.data?.config?.enableHttps || false,
  maxBodySize: props.node?.data?.config?.maxBodySize || 10485760,
  dataFormat: props.node?.data?.config?.dataFormat || 'AUTO',
});

const saveConfig = async () => {
  if (!formData.port || formData.port < 1 || formData.port > 65535) {
    ElMessage.error('请输入有效的端口号 (1-65535)');
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
