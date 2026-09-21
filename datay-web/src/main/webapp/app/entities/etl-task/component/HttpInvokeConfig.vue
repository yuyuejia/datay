<template>
  <form name="editForm" novalidate>
    <div class="curl-import">
      <el-button size="small" @click="showCurlImport = !showCurlImport">
        {{ showCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
      </el-button>
      <span class="curl-import-tip">粘贴 cURL 命令，自动填充请求地址、方法、请求头和请求体</span>
      <div v-if="showCurlImport" class="curl-import-panel">
        <textarea
          class="form-control"
          v-model="curlText"
          :rows="4"
          placeholder="例如: curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -d '{&quot;name&quot;:&quot;tom&quot;}'"
        ></textarea>
        <div class="curl-import-actions">
          <el-button type="primary" size="small" @click="applyCurl">解析并填充</el-button>
          <el-button size="small" @click="clearCurlImport">取消</el-button>
        </div>
      </div>
    </div>
    <div class="form-group">
      <label for="url">请求URL</label>
      <input type="text" class="form-control" id="url" name="url" v-model="formData.url" placeholder="请输入HTTP请求URL" required />
    </div>
    <div class="form-group">
      <label for="method">请求方法</label>
      <select class="form-control" v-model="formData.method">
        <option value="GET">GET</option>
        <option value="POST">POST</option>
        <option value="PUT">PUT</option>
        <option value="PATCH">PATCH</option>
        <option value="DELETE">DELETE</option>
        <option value="HEAD">HEAD</option>
      </select>
    </div>
    <div class="form-group" v-if="hasBody">
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
  </form>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import { parseCurl } from '@/shared/util/curl';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

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

const showCurlImport = ref(false);
const curlText = ref('');

const hasBody = computed(() => formData.method !== 'GET' && formData.method !== 'HEAD');

const parseHeadersText = () => {
  if (!formData.headersText.trim()) {
    return {};
  }
  try {
    return JSON.parse(formData.headersText);
  } catch {
    return {};
  }
};

const applyCurl = () => {
  if (!curlText.value.trim()) {
    ElMessage.warning('请先粘贴 cURL 命令');
    return;
  }
  const parsed = parseCurl(curlText.value);
  if (!parsed.url) {
    ElMessage.error('未能从 cURL 命令中解析出请求URL');
    return;
  }
  formData.url = parsed.url;
  if (parsed.method) {
    formData.method = parsed.method;
  }
  if (parsed.body !== undefined) {
    formData.body = parsed.body;
  }
  if (parsed.timeout) {
    formData.timeout = parsed.timeout;
  }
  if (parsed.sslVerify === false) {
    formData.sslVerify = false;
  }
  const mergedHeaders = { ...parseHeadersText(), ...parsed.headers };
  formData.headersText = JSON.stringify(mergedHeaders, null, 2);

  if (parsed.warnings.length > 0) {
    ElMessage.warning(parsed.warnings.join('；'));
  } else {
    ElMessage.success('已根据 cURL 命令填充配置');
  }
  clearCurlImport();
};

const clearCurlImport = () => {
  curlText.value = '';
  showCurlImport.value = false;
};

const saveConfig = async () => {
  if (!formData.url.trim()) {
    ElMessage.error('请输入请求URL');
    return;
  }
  let headers = {};
  if (formData.headersText.trim()) {
    try {
      headers = JSON.parse(formData.headersText);
    } catch {
      ElMessage.error('请求头JSON格式错误');
      return;
    }
  }
  emits('save', { ...formData, headers });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.curl-import {
  margin-bottom: 20px;
  padding: 12px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  background-color: #fafafa;
}
.curl-import-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.curl-import-panel {
  margin-top: 10px;
}
.curl-import-actions {
  margin-top: 8px;
  text-align: right;
}
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
