<template>
  <el-form name="editForm" label-position="top">
    <div class="curl-import">
      <el-button size="small" @click="showCurlImport = !showCurlImport">
        {{ showCurlImport ? '收起 cURL 导入' : '从 cURL 导入' }}
      </el-button>
      <span class="curl-import-tip">粘贴 cURL 命令，自动填充请求地址、方法、请求头和请求体</span>
      <div v-if="showCurlImport" class="curl-import-panel">
        <el-input
          type="textarea"
          v-model="curlText"
          :rows="4"
          placeholder="例如: curl -X POST 'https://api.example.com/users' -H 'Content-Type: application/json' -d '{&quot;name&quot;:&quot;tom&quot;}'"
        />
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
    <el-form-item label="超时时间 (ms)">
      <el-input-number :controls="false" id="timeout" name="timeout" v-model="formData.timeout" :min="1000" />
    </el-form-item>
    <el-form-item v-if="hasBody" label="请求体">
      <el-input type="textarea" id="body" name="body" v-model="formData.body" :rows="4" placeholder="请输入JSON格式的请求体" />
    </el-form-item>
    <el-form-item label="请求头">
      <el-input
        type="textarea"
        id="headers"
        name="headers"
        v-model="formData.headersText"
        :rows="3"
        placeholder='JSON格式，如: {"Authorization": "Bearer token"}'
      />
    </el-form-item>
    <el-form-item label="数据路径">
      <el-input id="dataPath" name="dataPath" v-model="formData.dataPath" placeholder="从JSON响应中提取数据，如 $.std_data.result.rows" />
    </el-form-item>

    <el-divider content-position="left">分页拉取</el-divider>
    <el-form-item label="启用分页循环">
      <el-switch v-model="formData.paginationEnabled" />
    </el-form-item>
    <template v-if="formData.paginationEnabled">
      <div class="pagination-section">
        <div class="pagination-tip">请求体中使用 ${pageNo} 和 ${pageSize} 占位符，按页循环拉取；返回条数小于每页条数时停止。</div>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="起始页码">
              <el-input-number
                :controls="false"
                id="startPage"
                name="startPage"
                v-model="formData.startPage"
                :min="1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="每页条数">
              <el-input-number :controls="false" id="pageSize" name="pageSize" v-model="formData.pageSize" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="最大页数">
              <el-input-number :controls="false" id="maxPages" name="maxPages" v-model="formData.maxPages" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="间隔(ms)">
              <el-input-number
                :controls="false"
                id="requestInterval"
                name="requestInterval"
                v-model="formData.requestInterval"
                :min="0"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </template>
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
  sslVerify: props.node?.data?.config?.sslVerify !== false,
  paginationEnabled: props.node?.data?.config?.paginationEnabled === true,
  startPage: props.node?.data?.config?.startPage || 1,
  pageSize: props.node?.data?.config?.pageSize || 100,
  maxPages: props.node?.data?.config?.maxPages || 1000,
  dataPath: props.node?.data?.config?.dataPath || props.node?.data?.config?.jsonPath || '',
  requestInterval: props.node?.data?.config?.requestInterval || 0,
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
.pagination-section :deep(.el-form-item) {
  margin-bottom: 10px;
}
.pagination-tip {
  margin-bottom: 8px;
  color: #909399;
  font-size: 12px;
}
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
