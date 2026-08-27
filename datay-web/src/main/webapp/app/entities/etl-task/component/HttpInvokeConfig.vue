<template>
  <form name="editForm" novalidate>
    <div class="form-group">
      <label for="url">请求URL</label>
      <input type="text" class="form-control" id="url" name="url" v-model="formData.url" placeholder="请输入HTTP请求URL" required />
    </div>
    <div class="form-group">
      <label for="method">请求方法</label>
      <select class="form-control" v-model="formData.method">
        <option value="GET">GET</option>
        <option value="POST">POST</option>
      </select>
    </div>
    <div class="form-group" v-if="formData.method === 'POST'">
      <label for="body">请求体</label>
      <textarea class="form-control" id="body" name="body" v-model="formData.body" :rows="4" placeholder='请输入JSON格式的请求体'></textarea>
    </div>
    <div class="form-group">
      <label for="headers">请求头</label>
      <textarea class="form-control" id="headers" name="headers" v-model="formData.headersText" :rows="3" placeholder='JSON格式，如: {"Authorization": "Bearer token"}'></textarea>
    </div>
    <div class="form-group">
      <label for="jsonPath">JSON路径提取</label>
      <input type="text" class="form-control" id="jsonPath" name="jsonPath" v-model="formData.jsonPath" placeholder="从JSON响应中提取字段路径，如 $.data.items" />
      <small class="form-text text-muted"> 用于从JSON响应中提取特定字段 </small>
    </div>
    <div class="form-group">
      <label for="timeout">超时时间 (ms)</label>
      <input type="number" class="form-control" id="timeout" name="timeout" v-model.number="formData.timeout" :min="1000" />
    </div>
    <div class="form-group">
      <label for="retryCount">重试次数</label>
      <input type="number" class="form-control" id="retryCount" name="retryCount" v-model.number="formData.retryCount" :min="0" :max="10" />
    </div>
    <div class="form-group">
      <label for="retryInterval">重试间隔 (ms)</label>
      <input type="number" class="form-control" id="retryInterval" name="retryInterval" v-model.number="formData.retryInterval" :min="100" />
    </div>
    <div class="form-group">
      <label for="sslVerify">启用SSL证书验证</label>
      <el-switch v-model="formData.sslVerify" />
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

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save', 'cancel']);

const headersObj = ref(props.node?.data?.config?.headers || {});
const headersText = ref(JSON.stringify(headersObj.value, null, 2));

const formData = reactive({
  url: props.node?.data?.config?.url || '',
  method: props.node?.data?.config?.method || 'GET',
  headersText: headersText.value,
  body: props.node?.data?.config?.body || '',
  timeout: props.node?.data?.config?.timeout || 30000,
  retryCount: props.node?.data?.config?.retryCount || 1,
  retryInterval: props.node?.data?.config?.retryInterval || 1000,
  jsonPath: props.node?.data?.config?.jsonPath || '',
  sslVerify: props.node?.data?.config?.sslVerify !== false,
});

const saveConfig = async () => {
  if (!formData.url.trim()) {
    ElMessage.error('请输入请求URL');
    return;
  }
  let headers = {};
  if (formData.headersText.trim()) {
    try {
      headers = JSON.parse(formData.headersText);
    } catch (e) {
      ElMessage.error('请求头JSON格式错误');
      return;
    }
  }
  emits('save', { ...formData, headers });
};

const cancelConfig = () => {
  emits('cancel');
};
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
  font-family: monospace;
}
</style>
