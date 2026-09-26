<template>
  <el-form name="editForm" label-position="top">
    <div class="curl-import">
      <el-button size="small" @click="showCurlImport = !showCurlImport">
        {{ showCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
      </el-button>
      <span class="curl-import-tip">粘贴 cURL 命令，自动填充请求地址、方法、请求头和请求体</span>
      <div v-if="showCurlImport" class="curl-import-panel">
        <el-input type="textarea" v-model="curlText" :rows="4" placeholder="例如: curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -d '{&quot;name&quot;:&quot;tom&quot;}'" />
        <div class="curl-import-actions">
          <el-button type="primary" size="small" @click="applyCurl">解析并填充</el-button>
          <el-button size="small" @click="clearCurlImport">取消</el-button>
        </div>
      </div>
    </div>
    <el-form-item label="请求URL">
      <el-input id="url" name="url" v-model="formData.url" placeholder="请输入HTTP请求URL" required />
    </el-form-item>
    <el-form-item label="请求方法">
      <el-select v-model="formData.method">
            <el-option value="GET" label="GET" />
            <el-option value="POST" label="POST" />
            <el-option value="PUT" label="PUT" />
            <el-option value="PATCH" label="PATCH" />
            <el-option value="DELETE" label="DELETE" />
            <el-option value="HEAD" label="HEAD" />
          </el-select>
    </el-form-item>
    <el-form-item v-if="hasBody" label="请求体">
      <el-input type="textarea" id="body" name="body" v-model="formData.body" :rows="4" placeholder='请输入JSON格式的请求体' />
    </el-form-item>
    <el-form-item label="请求头">
      <el-input type="textarea" id="headers" name="headers" v-model="formData.headersText" :rows="3" placeholder='JSON格式，如: {"Authorization": "Bearer token"}' />
    </el-form-item>
    <el-form-item label="JSON路径提取">
      <el-input id="jsonPath" name="jsonPath" v-model="formData.jsonPath" placeholder="从JSON响应中提取字段路径，如 $.data.items" />
      <small class="form-text text-muted"> 用于从JSON响应中提取特定字段 </small>
    </el-form-item>
    <el-form-item label="超时时间 (ms)">
      <el-input-number :controls="false"  id="timeout" name="timeout" v-model="formData.timeout" ::min="1000" />
    </el-form-item>
    <el-form-item label="重试次数">
      <el-input-number :controls="false"  id="retryCount" name="retryCount" v-model="formData.retryCount" ::min="0" ::max="10" />
    </el-form-item>
    <el-form-item label="重试间隔 (ms)">
      <el-input-number :controls="false"  id="retryInterval" name="retryInterval" v-model="formData.retryInterval" ::min="100" />
    </el-form-item>
    <el-form-item label="启用SSL证书验证">
      <el-switch v-model="formData.sslVerify" />
    </el-form-item>
  </el-form>
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
