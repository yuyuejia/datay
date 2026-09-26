<template>
  <el-form name="editForm" label-position="top">
    <!-- 手写模式 -->
    <template v-if="editorMode === 'manual'">
    <!-- Java脚本代码输入区域 -->
    <el-form-item label="Java脚本代码">
      <el-input
        type="textarea"
        class="script-code-input"
        id="scriptCode"
        name="scriptCode"
        v-model="formData.scriptCode"
        :rows="15"
        :maxlength="10000"
        :placeholder='`请输入Java脚本代码，例如：
import com.data.job.FlowFile;
import com.data.job.component.javascript.ScriptContext.LogFunction;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

public class UserScript {
    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
        LogFunction log = (LogFunction) context.get("log");
        // 处理 FlowFile 中的 JSON 数组数据
        if (flowFile.getJsonArray() != null) {
            JSONArray records = flowFile.getJsonArray();
            JSONArray result = new JSONArray();
            for (Object item : records) {
                JSONObject record = (JSONObject) item;
                record.put("processed", true);
                result.add(record);
            }
            flowFile.setJsonArray(result);
            // 新增字段后同步表元数据，确保下游组件能识别
            flowFile.upsertColumnMeta("processed", "BOOLEAN");
            log.info("JSON数组处理完成，输出 " + result.size() + " 条");
        }
        return flowFile;
    }
}`'
      />
      <small class="form-text text-muted"> 支持Java语法，可以包含自定义的数据处理逻辑 </small>
    </el-form-item>

    <!-- 操作按钮 -->
    <div class="form-actions">
      <el-button type="info" @click="loadDefaultTemplate">加载默认模板</el-button>
      <el-button type="warning" @click="validateScript">验证语法</el-button>
      <el-button type="primary" @click="enterAssistant">AI 助手</el-button>
    </div>
    </template>

    <!-- AI 助手模式 -->
    <div v-else class="ai-assistant">
      <div class="ai-assistant-header">
        <span class="ai-assistant-title">代码生成助手</span>
        <span class="form-text text-muted">描述你的数据处理需求，我来生成符合组件规范的 Java 脚本</span>
        <el-button class="ai-assistant-back" text size="small" @click="exitAssistant">返回手写模式</el-button>
      </div>

      <el-alert v-if="statusLoaded && !aiAvailable" :title="statusMessage || 'AI 助手未配置，请联系管理员'" type="warning" :closable="false" show-icon />

      <template v-else>
        <div v-if="samples.length" class="ai-samples">
          <span class="form-text text-muted">试试：</span>
          <el-tag v-for="(sample, index) in samples" :key="index" class="ai-sample" @click="applySample(sample)">{{ sample }}</el-tag>
        </div>

        <div class="ai-upstream">
          <span class="form-text text-muted">上游调试数据</span>
          <el-button text size="small" :loading="upstreamLoading" @click="loadUpstreamData(true)">刷新</el-button>
          <span v-if="upstreamLoading" class="form-text text-muted">正在获取上游调试数据…</span>
          <span v-else-if="upstreamError" class="form-text text-danger">{{ upstreamError }}</span>
          <span v-else-if="upstreamData.length" class="form-text text-muted">
            已获取 {{ upstreamData.length }} 个上游组件的采样数据，生成时将一并提供给大模型
          </span>
          <span v-else-if="upstreamLoaded" class="form-text text-muted">暂无上游调试数据，本次生成不携带</span>
        </div>

        <el-input type="textarea" v-model="requirement" :rows="3" placeholder="例如：给 JSON 数组的每条记录增加 processed 标记和处理时间戳，时间戳用当前毫秒值" @keydown.enter.exact.prevent="generateCode" />

        <div class="ai-assistant-actions">
          <el-button type="primary" size="small" :loading="generating" @click="generateCode">生成代码</el-button>
          <el-button size="small" :disabled="generating || !requirement.trim()" @click="requirement = ''">清空</el-button>
        </div>

        <div v-if="generatedExplanation" class="ai-explanation">
          <div class="ai-explanation-title">处理说明</div>
          <div class="ai-explanation-body">{{ generatedExplanation }}</div>
        </div>

        <div v-if="generatedCode" class="ai-result">
          <pre class="ai-code-preview">{{ generatedCode }}</pre>
          <div class="ai-assistant-actions">
            <el-button type="success" size="small" @click="applyGeneratedCode">应用到编辑器</el-button>
          </div>
        </div>
      </template>
    </div>
  </el-form>
</template>

<script setup>
import { ref, reactive, inject } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import AiAssistantService from '@/entities/data-source/ai-assistant.service';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

// 配置项定义
const configItems = [
  {
    key: 'scriptCode',
    label: 'Java脚本代码',
    defaultValue: '',
    required: true,
    description: '输入Java脚本代码进行自定义数据处理',
    show: true,
    maxLength: 10000,
    rows: 15,
    controlType: 'TEXTAREA',
    parameterType: 'STRING',
  },
];

const formRef = ref(null);
const formData = reactive({
  scriptCode: props.node?.data?.config?.scriptCode || '',
});

// 默认脚本模板
const defaultScriptTemplate = `import com.data.job.FlowFile;
import com.data.job.component.javascript.ScriptContext.LogFunction;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import java.util.Map;

public class UserScript {
    public FlowFile process(FlowFile flowFile, Map<String, Object> context) {
        LogFunction log = (LogFunction) context.get("log");

        // 1. 处理 JSON 数组：逐条读取、修改并输出
        if (flowFile.getJsonArray() != null) {
            JSONArray records = flowFile.getJsonArray();
            JSONArray result = new JSONArray();
            for (Object item : records) {
                JSONObject record = (JSONObject) item;
                // CDC 场景：UPDATE/DELETE 事件包含变更前数据 __before，先处理它
                JSONObject before = record.getJSONObject("__before");
                if (before != null) {
                    before.put("__processed", true);
                    result.add(before);
                }
                // 处理当前记录：新增/修改字段
                record.put("processed", true);
                record.put("process_time", System.currentTimeMillis());
                result.add(record);
            }
            flowFile.setJsonArray(result);
            // 新增字段后同步表元数据，确保下游组件能识别
            flowFile.upsertColumnMeta("processed", "BOOLEAN");
            flowFile.upsertColumnMeta("process_time", "BIGINT");
            log.info("JSON数组处理完成，输出 " + result.size() + " 条");
        }

        // 2. 处理单个 JSON 对象
        if (flowFile.getJsonObject() != null) {
            JSONObject record = flowFile.getJsonObject();
            record.put("processed", true);
            record.put("process_time", System.currentTimeMillis());
            // 新增字段后同步表元数据，确保下游组件能识别
            flowFile.upsertColumnMeta("processed", "BOOLEAN");
            flowFile.upsertColumnMeta("process_time", "BIGINT");
            log.info("JSON对象处理完成");
        }

        // 3. 其它格式（CSV/TEXT/BINARY）原样透传
        return flowFile;
    }
}`;

// 加载默认模板
const loadDefaultTemplate = () => {
  formData.scriptCode = defaultScriptTemplate;
  ElMessage.success('已加载默认脚本模板');
};

// 脚本语法验证
const validateScript = () => {
  const scriptCode = formData.scriptCode.trim();

  if (!scriptCode) {
    ElMessage.warning('请输入Java脚本代码');
    return;
  }

  // 基础Java语法检查
  if (!scriptCode.includes('public class UserScript')) {
    ElMessage.warning('脚本应该包含 public class UserScript 类定义');
    return;
  }

  if (!scriptCode.includes('process(FlowFile flowFile, Map<String, Object> context)')) {
    ElMessage.warning('脚本应该包含 process 方法定义');
    return;
  }

  if (!scriptCode.includes('return flowFile')) {
    ElMessage.warning('process 方法应该返回 FlowFile 对象');
    return;
  }

  // 检查必要的导入
  const requiredImports = ['import com.data.job.FlowFile', 'import java.util.Map'];

  for (const requiredImport of requiredImports) {
    if (!scriptCode.includes(requiredImport)) {
      ElMessage.warning(`脚本应该包含必要的导入: ${requiredImport}`);
      return;
    }
  }

  ElMessage.success('Java脚本语法验证通过');
};

// ========== 代码生成助手 ==========
const ASSISTANT_ID = 'javascript';
const aiService = new AiAssistantService();
const designContext = inject('etlTaskDesignContext', null);

const editorMode = ref('manual');
const statusLoaded = ref(false);
const aiAvailable = ref(false);
const statusMessage = ref('');
const samples = ref([]);
const requirement = ref('');
const generating = ref(false);
const generatedCode = ref('');
const generatedExplanation = ref('');

// 上游调试数据：只要存在就默认携带给大模型，提升生成脚本的字段准确性
const upstreamLoaded = ref(false);
const upstreamLoading = ref(false);
const upstreamError = ref('');
const upstreamData = ref([]);

// 进入 AI 助手模式：首次进入时加载 AI 服务状态与上游调试数据
const enterAssistant = async () => {
  editorMode.value = 'assistant';
  if (!statusLoaded.value) {
    await loadAssistantStatus();
  }
  if (!upstreamLoaded.value && !upstreamLoading.value) {
    await loadUpstreamData();
  }
};

// 返回手写模式
const exitAssistant = () => {
  editorMode.value = 'manual';
};

// 获取上游组件的调试采样数据
const loadUpstreamData = async (force = false) => {
  if (!designContext || typeof designContext.debugUpstreamData !== 'function') {
    upstreamError.value = '当前环境无法获取上游调试数据';
    upstreamLoaded.value = true;
    return;
  }
  upstreamLoading.value = true;
  upstreamError.value = '';
  try {
    const res = await designContext.debugUpstreamData(props.node?.id, force);
    upstreamData.value = res.upstream || [];
    upstreamError.value = res.error || '';
  } catch (error) {
    upstreamData.value = [];
    upstreamError.value = error?.response?.data?.message || error?.message || '获取上游调试数据失败';
  } finally {
    upstreamLoading.value = false;
    upstreamLoaded.value = true;
  }
};

const loadAssistantStatus = async () => {
  try {
    const status = await aiService.getStatus();
    aiAvailable.value = status.available;
    statusMessage.value = status.message || '';
    if (status.available) {
      const assistants = await aiService.listAssistants();
      const current = assistants.find((item) => item.id === ASSISTANT_ID);
      samples.value = current?.samplePrompts || [];
    }
  } catch (error) {
    aiAvailable.value = false;
    statusMessage.value = error?.message || '无法获取 AI 助手状态';
  } finally {
    statusLoaded.value = true;
  }
};

const applySample = (sample) => {
  requirement.value = sample;
};

// 去掉回复中的 Java 代码块，说明区只保留文字，避免与代码预览重复
const cleanExplanation = (text) => {
  if (!text) return '';
  return text.replace(/```(?:java|Java|JAVA)?[\s\S]*?```/g, '').trim();
};

const generateCode = async () => {
  const text = requirement.value.trim();
  if (!text || generating.value) {
    if (!text) ElMessage.warning('请先描述你的数据处理需求');
    return;
  }

  generating.value = true;
  generatedCode.value = '';
  generatedExplanation.value = '';
  try {
    // 存在上游调试数据时默认携带，先确保已获取
    if (!upstreamLoaded.value) {
      await loadUpstreamData();
    }
    const contextData = upstreamData.value.length ? { upstream: upstreamData.value } : undefined;
    const result = await aiService.generate(text, undefined, [], ASSISTANT_ID, contextData);
    if (!result.available) {
      ElMessage.warning(result.explanation || 'AI 助手暂不可用');
      return;
    }
    generatedCode.value = result.code || result.sql || '';
    generatedExplanation.value = cleanExplanation(result.explanation);
    if (!generatedCode.value) {
      ElMessage.warning('未解析到可用的脚本，请调整描述后重试');
      return;
    }
    ElMessage.success('代码已生成，确认后可应用到编辑器');
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '生成失败，请稍后重试');
  } finally {
    generating.value = false;
  }
};

const applyGeneratedCode = () => {
  if (!generatedCode.value) return;
  formData.scriptCode = generatedCode.value;
  // 应用后返回手写模式，方便继续微调或校验
  editorMode.value = 'manual';
  ElMessage.success('已应用到编辑器');
};

// 保存配置
const saveConfig = async () => {
  const scriptCode = formData.scriptCode.trim();

  //   if (!scriptCode) {
  //     ElMessage.error('请输入Java脚本代码');
  //     return;
  //   }

  //   // 验证脚本基本结构
  //   if (!scriptCode.includes('public class UserScript') ||
  //       !scriptCode.includes('process(FlowFile flowFile, Map<String, Object> context)')) {
  //     ElMessage.error('脚本格式不正确，请检查类和方法定义');
  //     return;
  //   }

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
  font-family: 'Courier New', monospace;
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

.script-code-input {
  resize: vertical;
  min-height: 300px;
  font-family: 'Courier New', monospace;
  line-height: 1.4;
}

.ai-assistant {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #fafafa;
}

.ai-assistant-header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.ai-assistant-title {
  font-weight: 600;
  color: #303133;
}

.ai-assistant-back {
  margin-left: auto;
}

.ai-samples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.ai-sample {
  cursor: pointer;
}

.ai-upstream {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-upstream .form-text {
  margin-top: 0;
}

.text-danger {
  color: #f56c6c;
}

.ai-assistant-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.ai-explanation {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}

.ai-explanation-title {
  font-weight: 600;
  margin-bottom: 4px;
}

.ai-explanation-body {
  white-space: pre-wrap;
  line-height: 1.5;
}

.ai-result {
  margin-top: 12px;
}

.ai-code-preview {
  background-color: #f5f5f5;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 10px;
  margin: 0;
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
