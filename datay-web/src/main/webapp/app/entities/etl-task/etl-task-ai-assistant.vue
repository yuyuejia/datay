<template>
  <div class="etl-ai-root">
    <transition name="etl-ai-fade">
      <div v-if="visible" class="etl-ai-overlay" @click="emit('close')"></div>
    </transition>

    <transition name="etl-ai-slide-right">
      <div v-if="visible" class="etl-ai-drawer">
        <div class="etl-ai-drawer-header">
          <div class="etl-ai-drawer-header-left">
            <font-awesome-icon icon="robot" class="etl-ai-icon" />
            <span class="etl-ai-title">{{ assistantTitle }}</span>
            <span v-if="status.available" class="etl-ai-badge etl-ai-badge-success">
              {{ status.model || '已启用' }}
            </span>
            <span v-else class="etl-ai-badge etl-ai-badge-info">未配置</span>
          </div>
          <div class="etl-ai-drawer-header-actions">
            <button
              class="etl-ai-new-session-btn"
              :disabled="generating || messages.length === 0"
              title="清空当前对话，开启新会话"
              @click="newSession"
            >
              <font-awesome-icon icon="plus" />
              <span>新会话</span>
            </button>
            <button class="etl-ai-close-btn" title="关闭" @click="emit('close')">
              <font-awesome-icon icon="times" />
            </button>
          </div>
        </div>

        <div class="etl-ai-body">
          <div v-if="!status.available" class="etl-ai-warning-banner">
            <font-awesome-icon icon="exclamation-circle" class="etl-ai-warn-icon" />
            <div class="etl-ai-warn-content">
              <strong>AI 助手未启用</strong>
              <p>{{ status.message || '请在「服务配置 · 大模型」中配置 API Key 后使用' }}</p>
            </div>
          </div>

          <div v-if="tools.length" class="etl-ai-tools-bar">
            <span class="etl-ai-tools-title">已注册工具</span>
            <span v-for="tool in tools" :key="tool.name" class="etl-ai-tool-tag" :title="tool.description">
              {{ tool.name }}
            </span>
          </div>

          <div ref="messageListRef" class="etl-ai-messages">
            <div v-if="messages.length === 0" class="etl-ai-empty">
              <font-awesome-icon icon="wand-magic-sparkles" class="etl-ai-empty-icon" />
              <p>{{ emptyHint }}</p>
              <p class="etl-ai-empty-hint">生成后先预览，再点击「应用到画布」；不会自动保存或执行任务。</p>
              <div v-if="samples.length" class="etl-ai-samples">
                <button v-for="sample in samples" :key="sample" class="etl-ai-sample-btn" @click="applySample(sample)">
                  {{ sample }}
                </button>
              </div>
            </div>

            <div
              v-for="(message, index) in messages"
              :key="index"
              class="etl-ai-message"
              :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
            >
              <div class="etl-ai-bubble">
                <template v-if="message.role === 'user'">
                  <div class="etl-ai-user-text">{{ message.content }}</div>
                </template>
                <template v-else>
                  <div v-if="message.loading" class="etl-ai-loading">
                    <font-awesome-icon icon="circle-notch" spin />
                    <span>{{ message.statusText || '正在设计任务…' }}</span>
                  </div>
                  <template v-else>
                    <div v-if="message.toolCalls && message.toolCalls.length" class="etl-ai-tool-traces">
                      <button class="etl-ai-trace-toggle" @click="toggleTools(index)">
                        工具调用（{{ message.toolCalls.length }}）{{ expanded[index] ? '收起' : '展开' }}
                      </button>
                      <ul v-if="expanded[index]" class="etl-ai-tool-trace-list">
                        <li v-for="(tool, toolIndex) in message.toolCalls" :key="toolIndex" class="etl-ai-tool-trace">
                          <font-awesome-icon
                            :icon="tool.success ? 'check-circle' : 'times-circle'"
                            :class="tool.success ? 'is-ok' : 'is-fail'"
                          />
                          <span class="etl-ai-trace-name">{{ tool.name }}</span>
                          <span class="etl-ai-trace-desc">{{ formatArgs(tool.arguments) }}</span>
                          <span class="etl-ai-trace-time">{{ tool.elapsedMs }}ms</span>
                        </li>
                      </ul>
                    </div>

                    <pre v-if="message.explanation" class="etl-ai-text">{{ message.explanation }}</pre>

                    <div v-if="message.spec" class="etl-ai-result">
                      <div class="etl-ai-result-title">
                        <font-awesome-icon icon="project-diagram" />
                        <span>{{ summaryOf(message).taskName || '已生成任务定义' }}</span>
                      </div>
                      <div class="etl-ai-result-meta">
                        <span>{{ summaryOf(message).nodeCount }} 个节点</span>
                        <span>{{ summaryOf(message).edgeCount }} 条连线</span>
                        <span v-if="summaryOf(message).emptyConfigNodes.length" class="is-warning">
                          {{ summaryOf(message).emptyConfigNodes.length }} 个待配置
                        </span>
                      </div>
                      <div v-if="summaryOf(message).emptyConfigNodes.length" class="etl-ai-skeleton">
                        待手动配置：{{ summaryOf(message).emptyConfigNodes.join('、') }}
                      </div>
                      <ul v-if="message.warnings && message.warnings.length" class="etl-ai-warnings">
                        <li v-for="(warning, warningIndex) in message.warnings" :key="warningIndex">{{ warning }}</li>
                      </ul>
                      <div class="etl-ai-result-actions">
                        <el-button type="primary" size="small" :disabled="message.applied" @click="applySpec(message)">
                          {{ message.applied ? '已应用到画布' : '应用到画布' }}
                        </el-button>
                      </div>
                    </div>
                  </template>
                </template>
              </div>
            </div>
          </div>
        </div>

        <div class="etl-ai-footer">
          <textarea
            v-model="input"
            class="etl-ai-textarea"
            :rows="2"
            placeholder="描述你的需求，Enter 发送，Shift+Enter 换行"
            :disabled="generating || !status.available"
            @keydown="onKeydown"
          ></textarea>
          <button class="etl-ai-send-btn" :class="{ loading: generating }" :disabled="!canSend" @click="send">
            <font-awesome-icon v-if="generating" icon="circle-notch" spin />
            <span>{{ generating ? '生成中…' : '发送' }}</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import AiAssistantService from '@/entities/data-source/ai-assistant.service';
import type { AiAssistantInfo, AiHistoryMessage, AiStatus, AiToolTrace } from '@/entities/data-source/ai-assistant.service';
import { summarizeEtlSpec } from './etl-task-ai-spec';
import type { EtlAiSummary, EtlAiTaskSpec } from './etl-task-ai-spec';

const AI_ASSISTANT_ID = 'etl-task-design';

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  loading?: boolean;
  statusText?: string;
  explanation?: string;
  toolCalls?: AiToolTrace[];
  spec?: EtlAiTaskSpec;
  warnings?: string[];
  applied?: boolean;
}

const props = defineProps<{
  visible: boolean;
  currentTask: Record<string, any>;
  hasCanvasContent: boolean;
}>();

const emit = defineEmits<{
  close: [];
  apply: [spec: EtlAiTaskSpec];
}>();

const aiService = new AiAssistantService();
const status = ref<AiStatus>({ available: false });
const assistantInfo = ref<AiAssistantInfo | null>(null);
const messages = ref<ChatMessage[]>([]);
const input = ref('');
const generating = ref(false);
const expanded = ref<Record<number, boolean>>({});
const messageListRef = ref<HTMLElement | null>(null);

const assistantTitle = computed(() => assistantInfo.value?.name || 'AI ETL 设计助手');
const emptyHint = computed(() => assistantInfo.value?.description || '用一句话描述数据流向，AI 会自动选择组件、生成节点与连线。');
const samples = computed(() => assistantInfo.value?.samplePrompts ?? []);
const tools = computed(() => assistantInfo.value?.tools ?? []);
const canSend = computed(() => !!input.value.trim() && !generating.value && status.value.available);

const scrollToBottom = async () => {
  await nextTick();
  const el = messageListRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
};

const loadStatus = async () => {
  try {
    status.value = await aiService.getStatus();
  } catch {
    status.value = { available: false, message: 'AI 服务不可用，请检查后端服务' };
  }
};

const loadAssistant = async () => {
  try {
    const assistants = await aiService.listAssistants();
    assistantInfo.value = assistants.find(item => item.id === AI_ASSISTANT_ID) ?? null;
  } catch {
    assistantInfo.value = null;
  }
};

const buildHistory = (): AiHistoryMessage[] =>
  messages.value
    .filter(message => !message.loading && (message.content || message.explanation))
    .slice(-8)
    .map(message => ({
      role: message.role,
      content: message.role === 'assistant' ? message.explanation || '' : message.content,
    }));

const cleanExplanation = (text?: string): string => (text ? text.replace(/```[\s\S]*?```/g, '').trim() : '');

const send = async () => {
  const text = input.value.trim();
  if (!text || generating.value || !status.value.available) {
    return;
  }
  const history = buildHistory();
  messages.value.push({ role: 'user', content: text });
  input.value = '';
  generating.value = true;

  const placeholderIndex = messages.value.push({ role: 'assistant', content: '', loading: true, statusText: '正在查询组件目录…' }) - 1;
  const placeholder = messages.value[placeholderIndex];
  await scrollToBottom();

  const slowTimer = window.setTimeout(() => {
    placeholder.statusText = '正在读取组件帮助文档…';
  }, 2500);
  const slowerTimer = window.setTimeout(() => {
    placeholder.statusText = '正在校验数据源与表结构…';
  }, 6000);

  try {
    const result = await aiService.generate(text, undefined, history, AI_ASSISTANT_ID, {
      currentTask: props.currentTask,
    });
    placeholder.loading = false;
    placeholder.toolCalls = result.toolCalls || [];
    placeholder.warnings = result.warnings || [];
    placeholder.explanation = cleanExplanation(result.explanation);
    if (!result.available) {
      placeholder.explanation = result.explanation || 'AI 助手未配置';
      return;
    }
    if (result.etlTaskSpec) {
      placeholder.spec = result.etlTaskSpec as EtlAiTaskSpec;
      if (!placeholder.warnings.length && placeholder.spec.warnings) {
        placeholder.warnings = placeholder.spec.warnings;
      }
    } else if (!placeholder.explanation) {
      placeholder.explanation = '未能解析到任务定义，请补充源/目标数据源与表名后重试。';
    }
  } catch (error: any) {
    placeholder.loading = false;
    placeholder.explanation = error?.response?.data?.message || error?.message || '生成失败，请稍后重试';
  } finally {
    window.clearTimeout(slowTimer);
    window.clearTimeout(slowerTimer);
    generating.value = false;
    await scrollToBottom();
  }
};

const applySpec = async (message: ChatMessage) => {
  if (!message.spec) {
    return;
  }
  if (props.hasCanvasContent) {
    try {
      await ElMessageBox.confirm('应用后将替换当前画布中的节点与连线，是否继续？', '应用 AI 设计', {
        type: 'warning',
        confirmButtonText: '应用',
        cancelButtonText: '取消',
      });
    } catch {
      return;
    }
  }
  emit('apply', message.spec);
  message.applied = true;
  ElMessage.success('已应用到画布，请检查节点配置后保存');
};

const newSession = () => {
  messages.value = [];
  input.value = '';
  expanded.value = {};
};

const applySample = (sample: string) => {
  input.value = sample;
};

const onKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault();
    void send();
  }
};

const formatArgs = (args?: Record<string, any>): string => {
  if (!args || Object.keys(args).length === 0) {
    return '';
  }
  try {
    const text = JSON.stringify(args);
    return text.length > 120 ? `${text.slice(0, 120)}…` : text;
  } catch {
    return '';
  }
};

const toggleTools = (index: number) => {
  expanded.value[index] = !expanded.value[index];
};

const summaryOf = (message: ChatMessage): EtlAiSummary => summarizeEtlSpec(message.spec);

watch(
  () => props.visible,
  async visible => {
    if (visible) {
      await scrollToBottom();
    }
  },
);

onMounted(async () => {
  await loadStatus();
  await loadAssistant();
  if (props.visible) {
    await scrollToBottom();
  }
});
</script>

<style scoped>
.etl-ai-root {
  position: relative;
}

/* ===== 遮罩层 ===== */
.etl-ai-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  /* 必须高于顶部导航（.app-topbar 的 1030），否则抽屉头部会被菜单栏盖住 */
  z-index: 1040;
  background: rgba(0, 0, 0, 0.4);
}

/* ===== 抽屉面板 ===== */
.etl-ai-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 1041;
  width: 520px;
  max-width: 100vw;
  display: flex;
  flex-direction: column;
  background: #fff;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.12);
}

/* ===== 过渡动画 ===== */
.etl-ai-fade-enter-active,
.etl-ai-fade-leave-active {
  transition: opacity 0.3s ease;
}

.etl-ai-fade-enter-from,
.etl-ai-fade-leave-to {
  opacity: 0;
}

.etl-ai-slide-right-enter-active,
.etl-ai-slide-right-leave-active {
  transition: transform 0.3s ease;
}

.etl-ai-slide-right-enter-from,
.etl-ai-slide-right-leave-to {
  transform: translateX(100%);
}

/* ===== 头部 ===== */
.etl-ai-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.etl-ai-drawer-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.etl-ai-icon {
  color: #409eff;
}

.etl-ai-close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #909399;
  font-size: 16px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.etl-ai-close-btn:hover {
  background: #f0f2f5;
  color: #303133;
}

.etl-ai-drawer-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.etl-ai-new-session-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.etl-ai-new-session-btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}

.etl-ai-new-session-btn:disabled {
  color: #c0c4cc;
  border-color: #ebeef5;
  cursor: not-allowed;
}

/* ===== 标签徽章 ===== */
.etl-ai-badge {
  display: inline-block;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 3px;
  line-height: 1.5;
}

.etl-ai-badge-success {
  color: #67c23a;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}

.etl-ai-badge-info {
  color: #909399;
  background: #f4f4f5;
  border: 1px solid #e9e9eb;
}

/* ===== 主体 ===== */
.etl-ai-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 警告横幅 ===== */
.etl-ai-warning-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin: 12px;
  padding: 10px 14px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  color: #e6a23c;
  font-size: 13px;
}

.etl-ai-warn-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.etl-ai-warn-content strong {
  display: block;
  margin-bottom: 2px;
  color: #e6a23c;
}

.etl-ai-warn-content p {
  margin: 0;
  color: #909399;
}

/* ===== 已注册工具 ===== */
.etl-ai-tools-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.etl-ai-tools-title {
  font-size: 12px;
  color: #909399;
}

.etl-ai-tool-tag {
  display: inline-block;
  padding: 0 6px;
  font-size: 11px;
  line-height: 1.8;
  color: #606266;
  background: #f0f2f5;
  border-radius: 3px;
  white-space: nowrap;
  cursor: default;
}

/* ===== 消息列表 ===== */
.etl-ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #fff;
}

/* ===== 空状态 ===== */
.etl-ai-empty {
  text-align: center;
  color: #909399;
  padding-top: 48px;
}

.etl-ai-empty-icon {
  font-size: 32px;
  color: #a0cfff;
  margin-bottom: 12px;
}

.etl-ai-empty p {
  margin: 0 0 8px;
  font-size: 14px;
}

.etl-ai-empty-hint {
  font-size: 12px;
  color: #a8abb2;
}

.etl-ai-samples {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 14px;
}

.etl-ai-sample-btn {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  color: #409eff;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}

.etl-ai-sample-btn:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

/* ===== 消息气泡 ===== */
.etl-ai-message {
  display: flex;
  margin-bottom: 14px;
}

.etl-ai-message.is-user {
  justify-content: flex-end;
}

.etl-ai-bubble {
  max-width: 100%;
  min-width: 40px;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
}

.is-user .etl-ai-bubble {
  max-width: 85%;
  background: #409eff;
  color: #fff;
}

.is-assistant .etl-ai-bubble {
  width: 100%;
  background: #f5f7fa;
  color: #303133;
}

.etl-ai-user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== 加载状态 ===== */
.etl-ai-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

/* ===== 工具调用轨迹 ===== */
.etl-ai-tool-traces {
  margin-bottom: 8px;
}

.etl-ai-trace-toggle {
  border: none;
  background: transparent;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.etl-ai-tool-trace-list {
  margin: 6px 0 0;
  padding-left: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.etl-ai-tool-trace {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.etl-ai-tool-trace .is-ok {
  color: #67c23a;
}

.etl-ai-tool-trace .is-fail {
  color: #f56c6c;
}

.etl-ai-trace-name {
  font-family: monospace;
  color: #606266;
  flex-shrink: 0;
}

.etl-ai-trace-desc {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.etl-ai-trace-time {
  flex-shrink: 0;
}

/* ===== 结果卡片 ===== */
.etl-ai-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
}

.etl-ai-result {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  background: #fff;
}

.etl-ai-result-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}

.etl-ai-result-meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.etl-ai-result-meta .is-warning {
  color: #e6a23c;
}

.etl-ai-skeleton {
  margin-top: 6px;
  font-size: 12px;
  color: #e6a23c;
}

.etl-ai-warnings {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #e6a23c;
  max-height: 120px;
  overflow-y: auto;
}

.etl-ai-result-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

/* ===== 底部输入 ===== */
.etl-ai-footer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}

.etl-ai-textarea {
  flex: 1;
  padding: 8px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  font-family: inherit;
  line-height: 1.5;
  color: #303133;
  background: #fff;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}

.etl-ai-textarea:focus {
  border-color: #409eff;
}

.etl-ai-textarea:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  cursor: not-allowed;
}

.etl-ai-textarea::placeholder {
  color: #c0c4cc;
}

.etl-ai-send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: none;
  border-radius: 4px;
  background: #409eff;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.etl-ai-send-btn:hover:not(:disabled) {
  background: #337ecc;
}

.etl-ai-send-btn:active:not(:disabled) {
  background: #2a6eb3;
}

.etl-ai-send-btn:disabled {
  background: #a0cfff;
  color: #fff;
  cursor: not-allowed;
}

.etl-ai-send-btn.loading {
  opacity: 0.8;
}
</style>
