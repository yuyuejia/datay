<template>
  <form name="editForm" novalidate>
    <el-alert
      title="接口地址、API Key、模型等参数由系统统一配置，任务中无需填写。"
      type="info"
      :closable="false"
      show-icon
      class="config-tip"
    />

    <div class="form-group">
      <label for="systemPrompt">
        系统提示词
        <DynamicParameterHelp />
      </label>
      <textarea
        class="form-control"
        id="systemPrompt"
        name="systemPrompt"
        v-model="formData.systemPrompt"
        :rows="4"
        placeholder="例如：你是一个文本分析助手，请按指定 JSON 格式返回结果。"
      ></textarea>
      <small class="form-text text-muted">支持动态参数：<code>${字段名}</code> 取当前行字段，<code>${attr:属性名}</code> 取上游属性，<code>#{...}</code> 引用内置参数</small>
    </div>

    <div class="form-group">
      <label for="userPrompt">
        用户输入
        <DynamicParameterHelp />
      </label>
      <textarea
        class="form-control"
        id="userPrompt"
        name="userPrompt"
        v-model="formData.userPrompt"
        :rows="6"
        placeholder="例如：请分析下面这段用户评论的情感倾向：${content}"
      ></textarea>
      <small class="form-text text-muted">
        组件按行处理，用户输入中的 <code>${字段名}</code> 会按当前行数据赋值；输出为 JSON 时属性扩充到该行，否则写入
        <code>llm_result</code> 字段
      </small>
    </div>
  </form>
</template>

<script setup>
import { reactive } from 'vue';
import { defineProps, defineEmits } from 'vue';
import { ElMessage } from 'element-plus';
import DynamicParameterHelp from '../DynamicParameterHelp.vue';

const props = defineProps({
  node: Object,
});

const emits = defineEmits(['save']);

const config = props.node?.data?.config || {};

const formData = reactive({
  systemPrompt: config.systemPrompt || '',
  userPrompt: config.userPrompt || '',
});

const saveConfig = async () => {
  if (!formData.userPrompt.trim()) {
    ElMessage.error('请输入用户输入');
    return;
  }
  emits('save', { ...formData });
};

defineExpose({ saveConfig });
</script>

<style scoped>
.config-tip {
  margin-bottom: 20px;
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
  display: block;
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}

textarea {
  resize: vertical;
  font-family: 'Courier New', monospace;
  line-height: 1.4;
}

code {
  padding: 1px 5px;
  border-radius: 3px;
  background-color: #f5f7fa;
  color: #409eff;
  font-family: Menlo, Monaco, Consolas, monospace;
}
</style>
