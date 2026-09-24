<template>
  <div class="metric-ai-page">
    <div class="page-header">
      <div class="header-left">
        <font-awesome-icon icon="wand-magic-sparkles" class="header-icon" />
        <div class="title-block">
          <h4>智能问数</h4>
          <p class="subtitle">
            用一句话描述你想看的指标，自动拆解指标、维度、业务限定与时间范围并查询数据
          </p>
        </div>
      </div>
      <div class="header-right">
        <span v-if="status.available" class="ai-badge ai-badge-success">
          {{ status.model }}
        </span>
        <span v-else class="ai-badge ai-badge-info">未配置</span>
        <el-tooltip
          :content="
            ragStatus && ragStatus.builtAt
              ? '构建时间：' + ragStatus.builtAt
              : '尚未构建知识索引'
          "
          placement="bottom"
        >
          <span
            class="rag-status"
            :class="{ 'is-ready': ragStatus && ragStatus.indexed }"
          >
            <font-awesome-icon icon="database" />
            <span>{{ ragStatusText }}</span>
          </span>
        </el-tooltip>
        <el-button
          :loading="rebuilding"
          :disabled="!ragStatus || !ragStatus.enabled"
          @click="rebuildRag"
        >
          <font-awesome-icon v-if="!rebuilding" icon="sync" class="mr-1" />
          <span>重建知识索引</span>
        </el-button>
        <el-button @click="newSession" :disabled="generating">
          <font-awesome-icon icon="plus" class="mr-1" />
          <span>新会话</span>
        </el-button>
      </div>
    </div>

    <div v-if="!status.available" class="warning-banner">
      <font-awesome-icon icon="exclamation-circle" class="warn-icon" />
      <div class="warn-content">
        <strong>AI 助手未启用</strong>
        <p>{{ status.message || "请在服务端配置 datay.ai.api-key 后使用" }}</p>
      </div>
    </div>

    <div class="chat-panel">
      <div ref="messageListRef" class="messages">
        <div v-if="messages.length === 0" class="empty-state">
          <font-awesome-icon icon="wand-magic-sparkles" class="empty-icon" />
          <p>{{ emptyHint }}</p>
          <div class="samples">
            <button
              v-for="sample in samples"
              :key="sample"
              class="sample-btn"
              @click="onSample(sample)"
            >
              {{ sample }}
            </button>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="message"
          :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
        >
          <div class="bubble">
            <template v-if="msg.role === 'user'">
              <div class="user-text">{{ msg.content }}</div>
            </template>

            <template v-else>
              <div v-if="msg.loading" class="loading">
                <font-awesome-icon icon="circle-notch" spin />
                <span>{{ msg.statusText || "正在思考…" }}</span>
              </div>

              <template v-else>
                <div
                  v-if="msg.toolCalls && msg.toolCalls.length"
                  class="tool-traces"
                >
                  <button class="tool-traces-toggle" @click="toggleTools(idx)">
                    <font-awesome-icon
                      :icon="toolsExpanded[idx] ? 'caret-down' : 'caret-right'"
                    />
                    <span>工具调用 {{ msg.toolCalls.length }} 次</span>
                  </button>
                  <div v-if="toolsExpanded[idx]" class="tool-trace-list">
                    <div
                      v-for="(trace, ti) in msg.toolCalls"
                      :key="ti"
                      class="tool-trace"
                    >
                      <font-awesome-icon
                        :icon="trace.success ? 'check-circle' : 'times-circle'"
                        :class="trace.success ? 'ok' : 'fail'"
                      />
                      <span class="trace-name">{{ trace.name }}</span>
                      <span class="trace-desc">{{
                        formatArgs(trace.arguments)
                      }}</span>
                      <span class="trace-time">{{ trace.elapsedMs }}ms</span>
                    </div>
                  </div>
                </div>

                <div
                  v-if="msg.explanation"
                  class="ai-text"
                  v-html="renderMarkdown(msg.explanation)"
                ></div>

                <div v-if="msg.meta" class="meta">
                  <span v-if="msg.meta.rounds"
                    >循环 {{ msg.meta.rounds }} 轮</span
                  >
                  <span v-if="msg.meta.toolCalls !== undefined">
                    工具调用 {{ msg.meta.toolCalls }} 次
                  </span>
                  <span v-if="msg.meta.tokens"
                    >{{ msg.meta.tokens }} tokens</span
                  >
                </div>
              </template>
            </template>
          </div>
        </div>
      </div>

      <div class="footer">
        <textarea
          v-model="input"
          class="textarea"
          :rows="2"
          placeholder="例如：最近一周北京的销售额（Enter 发送，Shift+Enter 换行）"
          :disabled="generating || !status.available"
          @keydown="onKeydown"
        ></textarea>
        <el-button
          type="primary"
          class="send-btn"
          :loading="generating"
          :disabled="!input.trim() || generating || !status.available"
          @click="send"
        >
          <span>{{ generating ? "生成中…" : "发送" }}</span>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./metric-ai.component.ts"></script>

<style scoped>
.metric-ai-page {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  padding: 16px 20px 20px;
  background: var(--el-bg-color, #fff);
  box-sizing: border-box;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 24px;
  color: #409eff;
}

.title-block h4 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.subtitle {
  margin: 2px 0 0;
  font-size: 12px;
  color: #909399;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-badge {
  display: inline-block;
  padding: 1px 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 3px;
  line-height: 1.8;
}

.ai-badge-success {
  color: #67c23a;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}

.ai-badge-info {
  color: #909399;
  background: #f4f4f5;
  border: 1px solid #e9e9eb;
}

.rag-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border: 1px solid #e9e9eb;
  border-radius: 3px;
  cursor: default;
}

.rag-status.is-ready {
  color: #409eff;
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.warning-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 12px;
  padding: 10px 14px;
  background: #fdf6ec;
  border: 1px solid #faecd8;
  border-radius: 6px;
  color: #e6a23c;
  font-size: 13px;
  flex-shrink: 0;
}

.warn-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.warn-content strong {
  display: block;
  margin-bottom: 2px;
}

.warn-content p {
  margin: 0;
  color: #909399;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-top: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
  min-height: 0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-state {
  text-align: center;
  color: #909399;
  padding-top: 60px;
}

.empty-icon {
  font-size: 36px;
  color: #a0cfff;
  margin-bottom: 12px;
}

.empty-state p {
  margin: 0 0 16px;
  font-size: 14px;
}

.samples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 720px;
  margin: 0 auto;
}

.sample-btn {
  padding: 8px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 18px;
  background: #fff;
  color: #409eff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.sample-btn:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

.message {
  display: flex;
  margin-bottom: 14px;
}

.message.is-user {
  justify-content: flex-end;
}

.bubble {
  max-width: 100%;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
}

.is-user .bubble {
  background: #409eff;
  color: #fff;
  max-width: 80%;
}

.is-assistant .bubble {
  background: #f5f7fa;
  color: #303133;
  width: 100%;
}

.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

.tool-traces {
  margin-bottom: 8px;
}

.tool-traces-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  color: #909399;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.tool-traces-toggle:hover {
  color: #409eff;
  border-color: #b3d8ff;
  background: #ecf5ff;
}

.tool-trace-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
  padding: 6px 8px;
  background: #fafafa;
  border-radius: 4px;
}

.tool-trace {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}

.tool-trace .ok {
  color: #67c23a;
}

.tool-trace .fail {
  color: #f56c6c;
}

.trace-name {
  font-family: monospace;
  color: #606266;
}

.trace-desc {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-time {
  flex-shrink: 0;
}

.ai-text {
  word-break: break-word;
  margin-bottom: 8px;
}

.ai-text :deep(p) {
  margin: 0 0 8px;
}

.ai-text :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-text :deep(h1),
.ai-text :deep(h2),
.ai-text :deep(h3),
.ai-text :deep(h4),
.ai-text :deep(h5),
.ai-text :deep(h6) {
  margin: 12px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.ai-text :deep(ul),
.ai-text :deep(ol) {
  margin: 0 0 8px;
  padding-left: 20px;
}

.ai-text :deep(code) {
  background: #e9eff6;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 0.9em;
}

.ai-text :deep(.md-code) {
  margin: 0 0 8px;
  padding: 10px 12px;
  background: #f0f2f5;
  border-radius: 6px;
  overflow-x: auto;
  font-family: "Menlo", "Monaco", "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
}

.ai-text :deep(.md-table) {
  width: 100%;
  border-collapse: collapse;
  margin: 4px 0 10px;
  font-size: 12px;
}

.ai-text :deep(.md-table th),
.ai-text :deep(.md-table td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
  text-align: left;
  vertical-align: top;
}

.ai-text :deep(.md-table thead th) {
  background: #f5f7fa;
  color: #303133;
  font-weight: 600;
  white-space: nowrap;
}

.ai-text :deep(.md-table tbody tr:nth-child(even)) {
  background: #fafafa;
}

.meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 11px;
  color: #a8abb2;
}

.footer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}

.textarea {
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

.textarea:focus {
  border-color: #409eff;
}

.textarea:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  cursor: not-allowed;
}

.send-btn {
  flex-shrink: 0;
}
</style>
