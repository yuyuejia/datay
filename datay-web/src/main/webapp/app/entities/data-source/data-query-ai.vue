<template>
  <el-drawer
    v-model="visible"
    title="AI SQL 助手"
    direction="rtl"
    size="520px"
    :destroy-on-close="false"
    class="ai-drawer"
  >
    <template #header>
      <div class="ai-drawer-header">
        <font-awesome-icon icon="robot" class="ai-icon" />
        <span>AI SQL 助手</span>
        <el-tag
          v-if="status.available"
          size="small"
          type="success"
          effect="plain"
          >{{ status.model }}</el-tag
        >
        <el-tag v-else size="small" type="info" effect="plain">未配置</el-tag>
      </div>
    </template>

    <div class="ai-body">
      <el-alert
        v-if="!status.available"
        type="warning"
        :closable="false"
        show-icon
        title="AI 助手未启用"
        :description="
          status.message || '请在服务端配置 datay.ai.api-key 后使用'
        "
        class="mb-2"
      />

      <div class="ai-tools" v-if="tools.length">
        <span class="tools-title">已注册工具</span>
        <el-tag
          v-for="tool in tools"
          :key="tool.name"
          size="small"
          effect="plain"
          class="tool-tag"
          :title="tool.description"
        >
          {{ tool.name }}
        </el-tag>
      </div>

      <div ref="messageListRef" class="ai-messages">
        <div v-if="messages.length === 0" class="ai-empty">
          <font-awesome-icon icon="wand-magic-sparkles" class="empty-icon" />
          <p>用一句话描述你想查什么，我来生成 SQL</p>
          <div class="ai-samples">
            <el-button
              v-for="sample in samples"
              :key="sample"
              size="small"
              text
              class="sample-btn"
              @click="applySample(sample)"
            >
              {{ sample }}
            </el-button>
          </div>
        </div>

        <div
          v-for="(msg, idx) in messages"
          :key="idx"
          class="ai-message"
          :class="msg.role === 'user' ? 'is-user' : 'is-assistant'"
        >
          <div class="bubble">
            <template v-if="msg.role === 'user'">
              <div class="user-text">{{ msg.content }}</div>
            </template>
            <template v-else>
              <div v-if="msg.loading" class="ai-loading">
                <font-awesome-icon icon="circle-notch" spin />
                <span>{{ msg.statusText || "正在思考…" }}</span>
              </div>
              <template v-else>
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

                <div v-if="msg.sql" class="sql-block">
                  <div class="sql-block-header">
                    <span>生成 SQL</span>
                    <div class="sql-actions">
                      <el-button size="small" text @click="copySql(msg.sql)">
                        <font-awesome-icon icon="copy" />
                        <span>复制</span>
                      </el-button>
                      <el-button
                        size="small"
                        type="primary"
                        text
                        @click="applySql(msg.sql)"
                      >
                        <font-awesome-icon icon="arrow-right-to-bracket" />
                        <span>应用到编辑器</span>
                      </el-button>
                    </div>
                  </div>
                  <pre class="sql-code"><code>{{ msg.sql }}</code></pre>
                </div>

                <div
                  v-if="msg.explanation"
                  class="ai-text"
                  v-html="renderMarkdown(msg.explanation)"
                ></div>

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

    <template #footer>
      <div class="ai-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="描述你的数据需求，Enter 发送，Shift+Enter 换行"
          :disabled="generating || !status.available"
          @keydown="onKeydown"
        />
        <el-button
          type="primary"
          :loading="generating"
          :disabled="!input.trim() || !status.available"
          @click="send"
        >
          发送
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script lang="ts" src="./data-query-ai.component.ts"></script>

<style scoped>
.ai-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.ai-icon {
  color: var(--el-color-primary, #409eff);
}

.ai-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.ai-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
}

.tools-title {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.tool-tag {
  font-size: 11px;
}

.ai-messages {
  flex: 1;
  overflow: auto;
  padding: 12px;
  background: var(--el-fill-color-blank, #fff);
}

.ai-empty {
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  padding-top: 48px;
}

.empty-icon {
  font-size: 32px;
  color: var(--el-color-primary-light-5, #a0cfff);
  margin-bottom: 12px;
}

.ai-samples {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
}

.sample-btn {
  justify-content: flex-start;
  white-space: normal;
  text-align: left;
  height: auto;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
}

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
  background: var(--el-color-primary, #409eff);
  color: #fff;
  max-width: 85%;
}

.is-assistant .bubble {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
  width: 100%;
}

.user-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary, #909399);
}

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
  color: var(--el-text-color-secondary, #909399);
}

.tool-trace .ok {
  color: var(--el-color-success, #67c23a);
}

.tool-trace .fail {
  color: var(--el-color-danger, #f56c6c);
}

.trace-name {
  font-family: monospace;
  color: var(--el-text-color-regular, #606266);
}

.trace-desc {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sql-block {
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 8px;
}

.sql-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: var(--el-fill-color, #f0f2f5);
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
}

.sql-actions {
  display: flex;
  gap: 4px;
}

.sql-code {
  margin: 0;
  padding: 10px 12px;
  font-family: "Menlo", "Monaco", "Courier New", monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-primary, #303133);
}

.ai-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-text :deep(code) {
  background: var(--el-fill-color, #f0f2f5);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.ai-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.ai-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
</style>
