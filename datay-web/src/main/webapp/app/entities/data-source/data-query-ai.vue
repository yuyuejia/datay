<template>
  <div class="data-query-ai-root">
    <!-- 遮罩层 -->
    <transition name="fade">
      <div v-if="visible" class="ai-overlay" @click="close"></div>
    </transition>

    <!-- 抽屉面板 -->
    <transition name="slide-right">
      <div v-if="visible" class="ai-drawer">
      <!-- 头部 -->
      <div class="ai-drawer-header">
        <div class="ai-drawer-header-left">
          <font-awesome-icon icon="robot" class="ai-icon" />
          <span class="ai-title">{{ assistantTitle }}</span>
          <span
            v-if="status.available"
            class="ai-badge ai-badge-success"
            >{{ status.model }}</span
          >
          <span v-else class="ai-badge ai-badge-info">未配置</span>
        </div>
        <div class="ai-drawer-header-actions">
          <button
            class="ai-new-session-btn"
            :disabled="generating || (messages.length === 0 && !input)"
            @click="newSession"
            title="清空当前对话，开启新会话"
          >
            <font-awesome-icon icon="plus" />
            <span>新会话</span>
          </button>
          <button class="ai-close-btn" @click="close" title="关闭">
            <font-awesome-icon icon="times" />
          </button>
        </div>
      </div>

      <!-- 主体 -->
      <div class="ai-body">
        <!-- 未启用提示 -->
        <div
          v-if="!status.available"
          class="ai-warning-banner"
        >
          <font-awesome-icon icon="exclamation-circle" class="warn-icon" />
          <div class="warn-content">
            <strong>AI 助手未启用</strong>
            <p>{{ status.message || '请在服务端配置 datay.ai.api-key 后使用' }}</p>
          </div>
        </div>

        <!-- 已注册工具 -->
        <div v-if="tools.length" class="ai-tools">
          <span class="tools-title">已注册工具</span>
          <span
            v-for="tool in tools"
            :key="tool.name"
            class="ai-tool-tag"
            :title="tool.description"
          >
            {{ tool.name }}
          </span>
        </div>

        <!-- 消息列表 -->
        <div ref="messageListRef" class="ai-messages">
          <!-- 空状态 -->
          <div v-if="messages.length === 0" class="ai-empty">
            <font-awesome-icon icon="wand-magic-sparkles" class="empty-icon" />
            <p>{{ emptyHint }}</p>
            <div class="ai-samples">
              <button
                v-for="sample in samples"
                :key="sample"
                class="ai-sample-btn"
                @click="applySample(sample)"
              >
                {{ sample }}
              </button>
            </div>
          </div>

          <!-- 对话消息 -->
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="ai-message"
            :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
          >
            <div class="bubble">
              <!-- 用户消息 -->
              <template v-if="msg.role === 'user'">
                <div class="user-text">{{ msg.content }}</div>
              </template>

              <!-- AI 回复 -->
              <template v-else>
                <!-- 加载中 -->
                <div v-if="msg.loading" class="ai-loading">
                  <font-awesome-icon icon="circle-notch" spin />
                  <span>{{ msg.statusText || '正在思考…' }}</span>
                </div>

                <template v-else>
                  <!-- 工具调用轨迹 -->
                  <div
                    v-if="msg.toolCalls && msg.toolCalls.length"
                    class="tool-traces"
                  >
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

                  <!-- SQL 代码块 -->
                  <div v-if="msg.sql" class="sql-block">
                    <div class="sql-block-header">
                      <span>生成 SQL</span>
                      <div class="sql-actions">
                        <button
                          class="ai-text-btn"
                          @click="copySql(msg.sql)"
                          title="复制"
                        >
                          <font-awesome-icon icon="copy" />
                          <span>复制</span>
                        </button>
                        <button
                          class="ai-text-btn ai-text-btn-primary"
                          @click="applySql(msg.sql)"
                          title="应用到编辑器"
                        >
                          <font-awesome-icon icon="arrow-right-to-bracket" />
                          <span>应用到编辑器</span>
                        </button>
                      </div>
                    </div>
                    <pre class="sql-code"><code>{{ msg.sql }}</code></pre>
                  </div>

                  <!-- 解释文字 -->
                  <div
                    v-if="msg.explanation"
                    class="ai-text"
                    v-html="renderMarkdown(msg.explanation)"
                  ></div>

                  <!-- 元信息 -->
                  <div v-if="msg.meta" class="ai-meta">
                    <span v-if="msg.meta.rounds"
                      >循环 {{ msg.meta.rounds }} 轮</span
                    >
                    <span v-if="msg.meta.toolCalls !== undefined"
                      >工具调用 {{ msg.meta.toolCalls }} 次</span
                    >
                    <span v-if="msg.meta.tokens"
                      >{{ msg.meta.tokens }} tokens</span
                    >
                  </div>
                </template>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入 -->
      <div class="ai-footer">
        <textarea
          v-model="input"
          class="ai-textarea"
          :rows="2"
          :placeholder="inputPlaceholder"
          :disabled="generating || !status.available"
          @keydown="onKeydown"
        ></textarea>
        <button
          class="ai-send-btn"
          :class="{ loading: generating }"
          :disabled="!input.trim() || generating || !status.available"
          @click="send"
        >
          <font-awesome-icon
            v-if="generating"
            icon="circle-notch"
            spin
          />
          <span>{{ generating ? '生成中…' : '发送' }}</span>
        </button>
      </div>
    </div>
  </transition>
  </div>
</template>

<script lang="ts" src="./data-query-ai.component.ts"></script>

<style scoped>
/* ===== 遮罩层 ===== */
.ai-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.4);
  transition: opacity 0.3s ease;
}

/* ===== 抽屉面板 ===== */
.ai-drawer {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 1001;
  width: 520px;
  max-width: 100vw;
  display: flex;
  flex-direction: column;
  background: #fff;
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.12);
}

/* ===== 过渡动画 ===== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}

/* ===== 头部 ===== */
.ai-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.ai-drawer-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.ai-icon {
  color: #409eff;
}

.ai-close-btn {
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

.ai-close-btn:hover {
  background: #f0f2f5;
  color: #303133;
}

.ai-drawer-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-new-session-btn {
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

.ai-new-session-btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}

.ai-new-session-btn:disabled {
  color: #c0c4cc;
  border-color: #ebeef5;
  cursor: not-allowed;
}

/* ===== 标签徽章 ===== */
.ai-badge {
  display: inline-block;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 3px;
  line-height: 1.5;
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

/* ===== 主体 ===== */
.ai-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 警告横幅 ===== */
.ai-warning-banner {
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

.warn-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.warn-content strong {
  display: block;
  margin-bottom: 2px;
  color: #e6a23c;
}

.warn-content p {
  margin: 0;
  color: #909399;
}

/* ===== 工具标签 ===== */
.ai-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.tools-title {
  font-size: 12px;
  color: #909399;
}

.ai-tool-tag {
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
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  background: #fff;
}

/* ===== 空状态 ===== */
.ai-empty {
  text-align: center;
  color: #909399;
  padding-top: 48px;
}

.empty-icon {
  font-size: 32px;
  color: #a0cfff;
  margin-bottom: 12px;
}

.ai-empty p {
  margin: 0 0 16px;
  font-size: 14px;
}

.ai-samples {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
}

.ai-sample-btn {
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

.ai-sample-btn:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

/* ===== 消息气泡 ===== */
.ai-message {
  display: flex;
  margin-bottom: 14px;
}

.ai-message.is-user {
  justify-content: flex-end;
}

.bubble {
  max-width: 100%;
  min-width: 40px;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.6;
}

.is-user .bubble {
  background: #409eff;
  color: #fff;
  max-width: 85%;
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

/* ===== 加载状态 ===== */
.ai-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
}

/* ===== 工具调用轨迹 ===== */
.tool-traces {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
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

/* ===== SQL 代码块 ===== */
.sql-block {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 8px;
}

.sql-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: #f0f2f5;
  font-size: 12px;
  color: #606266;
}

.sql-actions {
  display: flex;
  gap: 4px;
}

.sql-code {
  margin: 0;
  padding: 10px 12px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #303133;
}

/* ===== 文本按钮 ===== */
.ai-text-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: none;
  background: transparent;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s;
}

.ai-text-btn:hover {
  background: #e4e7ed;
}

.ai-text-btn-primary {
  color: #409eff;
}

.ai-text-btn-primary:hover {
  background: #ecf5ff;
}

/* ===== 解释文字 ===== */
.ai-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-text :deep(code) {
  background: #f0f2f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 0.9em;
}

/* ===== 元信息 ===== */
.ai-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 11px;
  color: #a8abb2;
}

/* ===== 底部输入 ===== */
.ai-footer {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}

.ai-textarea {
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

.ai-textarea:focus {
  border-color: #409eff;
}

.ai-textarea:disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  cursor: not-allowed;
}

.ai-textarea::placeholder {
  color: #c0c4cc;
}

.ai-send-btn {
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

.ai-send-btn:hover:not(:disabled) {
  background: #337ecc;
}

.ai-send-btn:active:not(:disabled) {
  background: #2a6eb3;
}

.ai-send-btn:disabled {
  background: #a0cfff;
  color: #fff;
  cursor: not-allowed;
}

.ai-send-btn.loading {
  opacity: 0.8;
}
</style>