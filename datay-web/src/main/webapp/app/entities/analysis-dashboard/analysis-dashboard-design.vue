<template>
  <div class="dashboard-design">
    <div class="dashboard-design-chat">
      <div class="dashboard-design-chat-header">
        <div class="dashboard-design-chat-title">
          <font-awesome-icon
            icon="wand-magic-sparkles"
            class="mr-1"
          ></font-awesome-icon>
          <span>AI 看板设计</span>
        </div>
        <button type="button" class="dashboard-design-back" @click="goBack">
          ← 返回列表
        </button>
      </div>

      <div ref="messageListRef" class="dashboard-design-messages">
        <div v-if="messages.length === 0" class="dashboard-design-empty">
          <p>用一句话描述你想要的看板，AI 会自动确定数据集、图表与筛选器。</p>
          <div class="dashboard-design-samples">
            <span
              v-for="sample in samples"
              :key="sample"
              class="dashboard-design-sample"
              @click="onSample(sample)"
            >
              {{ sample }}
            </span>
          </div>
        </div>

        <div
          v-for="(message, index) in messages"
          :key="index"
          class="dashboard-design-message"
          :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
        >
          <div class="dashboard-design-bubble">
            <template v-if="message.loading">
              <span class="dashboard-design-status">{{
                message.statusText
              }}</span>
            </template>
            <template v-else-if="message.role === 'user'">
              {{ message.content }}
            </template>
            <template v-else>
              <pre class="dashboard-design-explanation">{{
                message.explanation
              }}</pre>
              <span v-if="message.hasSpec" class="dashboard-design-badge"
                >已生成看板定义</span
              >
              <div
                v-if="message.toolCalls && message.toolCalls.length"
                class="dashboard-design-tools"
              >
                <a class="dashboard-design-toggle" @click="toggleTools(index)">
                  工具调用（{{ message.toolCalls.length }}）{{
                    toolsExpanded[index] ? "收起" : "展开"
                  }}
                </a>
                <ul
                  v-if="toolsExpanded[index]"
                  class="dashboard-design-tool-list"
                >
                  <li
                    v-for="(tool, toolIndex) in message.toolCalls"
                    :key="toolIndex"
                  >
                    <span
                      :class="tool.success ? 'text-success' : 'text-danger'"
                      >{{ tool.name }}</span
                    >
                    <code v-if="formatArgs(tool.arguments)">{{
                      formatArgs(tool.arguments)
                    }}</code>
                  </li>
                </ul>
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="dashboard-design-input">
        <textarea
          v-model="input"
          class="dashboard-design-textarea"
          rows="3"
          :placeholder="
            spec
              ? '继续描述调整要求，例如：把销售趋势改成柱状图，并按区域拆分'
              : '例如：帮我做一个电商销售分析看板'
          "
          @keydown="onKeydown"
        ></textarea>
        <div class="dashboard-design-input-actions">
          <span class="dashboard-design-hint"
            >Enter 发送 / Shift+Enter 换行</span
          >
          <el-button type="primary" size="small" :disabled="generating" @click="send">
            {{ generating ? "生成中…" : "生成看板" }}
          </el-button>
        </div>
      </div>
    </div>

    <div class="dashboard-design-preview">
      <div class="dashboard-design-preview-header">
        <span>看板预览</span>
        <div class="dashboard-design-preview-actions">
          <el-button size="small" :disabled="!spec" @click="openSpecJson">
            查看/编辑 JSON
          </el-button>
          <el-button type="primary" size="small" @click="openSaveDialog">
            <font-awesome-icon icon="save" class="mr-1"></font-awesome-icon>
            保存看板
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="specError"
        type="error"
        :title="specError"
        :closable="false"
        class="mb-2"
      />

      <div v-if="spec" class="dashboard-design-preview-body">
        <DashboardFilters
          :filters="spec.filters || []"
          :model-value="filterValues"
          :spec="spec"
          @update:model-value="onFiltersChange"
        />
        <DashboardCanvas
          :spec="spec"
          :dataset-data="datasetData"
          :loading-datasets="loadingDatasets"
          :dataset-errors="datasetErrors"
        />
      </div>
      <el-empty v-else description="在左侧描述需求，AI 将在这里生成看板预览" />
    </div>

    <app-modal
      ref="saveModal"
      title="保存看板"
      size="md"
      :ok-title="saving ? '保存中…' : '保存并预览'"
      ok-variant="primary"
      cancel-title="取消"
      :ok-disabled="saving"
      @ok="save"
    >
      <div class="dashboard-design-form">
        <div class="dashboard-design-form-item">
          <label class="dashboard-design-form-label">名称</label>
          <input
            v-model="form.name"
            type="text"
            class="form-control"
            placeholder="请输入看板名称"
          />
        </div>
        <div class="dashboard-design-form-item">
          <label class="dashboard-design-form-label">描述</label>
          <textarea
            v-model="form.description"
            class="form-control"
            rows="2"
            placeholder="可选"
          ></textarea>
        </div>
      </div>
    </app-modal>

    <app-modal
      v-model="specJsonVisible"
      title="看板定义 JSON"
      size="lg"
      ok-title="刷新预览"
      ok-variant="primary"
      cancel-title="关闭"
      @ok="refreshFromSpecJson"
    >
      <el-alert
        v-if="specJsonError"
        type="error"
        :title="specJsonError"
        :closable="false"
        class="mb-2"
      />
      <p class="dashboard-design-json-hint">
        可手动修改下方 JSON，点击「刷新预览」校验并重新渲染看板。
      </p>
      <textarea
        v-model="specJsonText"
        class="dashboard-design-json"
        spellcheck="false"
      ></textarea>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./analysis-dashboard-design.component.ts"></script>

<style scoped>
.dashboard-design {
  display: flex;
  gap: 16px;
  height: calc(100vh - 120px);
  min-height: 560px;
  padding: 12px;
}

.dashboard-design-chat {
  display: flex;
  flex-direction: column;
  width: 420px;
  min-width: 340px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 6px rgb(0 0 0 / 10%);
}

.dashboard-design-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  color: #303133;
  font-weight: 600;
}

.dashboard-design-chat-title {
  display: inline-flex;
  align-items: center;
}

.dashboard-design-back {
  padding: 2px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  color: #606266;
  font-size: 12px;
  font-weight: 400;
  cursor: pointer;
  transition:
    color 0.2s,
    border-color 0.2s;
}

.dashboard-design-back:hover {
  border-color: #409eff;
  color: #409eff;
}

.dashboard-design-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.dashboard-design-empty {
  color: #909399;
  font-size: 13px;
}

.dashboard-design-samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.dashboard-design-sample {
  display: inline-block;
  padding: 3px 12px;
  border: 1px solid #d9ecff;
  border-radius: 14px;
  background: #ecf5ff;
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.dashboard-design-sample:hover {
  background: #d9ecff;
}

.dashboard-design-badge {
  display: inline-block;
  margin-top: 6px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f0f9eb;
  color: #67c23a;
  font-size: 12px;
}

.dashboard-design-toggle {
  color: #409eff;
  font-size: 12px;
  cursor: pointer;
}

.dashboard-design-message {
  display: flex;
  margin-bottom: 12px;
}

.dashboard-design-message.is-user {
  justify-content: flex-end;
}

.dashboard-design-bubble {
  max-width: 92%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f4f4f5;
  font-size: 13px;
  color: #303133;
  word-break: break-word;
}

.dashboard-design-message.is-user .dashboard-design-bubble {
  background: #ecf5ff;
}

.dashboard-design-explanation {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-word;
}

.dashboard-design-tools {
  margin-top: 6px;
}

.dashboard-design-tool-list {
  margin: 6px 0 0;
  padding-left: 18px;
  color: #606266;
  font-size: 12px;
}

.dashboard-design-tool-list code {
  margin-left: 6px;
  color: #909399;
}

.dashboard-design-input {
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}

.dashboard-design-textarea {
  display: block;
  box-sizing: border-box;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #303133;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}

.dashboard-design-textarea:focus {
  border-color: #409eff;
}

.dashboard-design-textarea::placeholder {
  color: #a8abb2;
}

.dashboard-design-input-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.dashboard-design-hint {
  color: #c0c4cc;
  font-size: 12px;
}

.dashboard-design-preview {
  flex: 1;
  min-width: 0;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 8px;
}

.dashboard-design-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  color: #303133;
  font-weight: 600;
}

.dashboard-design-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dashboard-design-json-hint {
  margin: 0 0 8px;
  color: #909399;
  font-size: 12px;
}

.dashboard-design-json {
  display: block;
  box-sizing: border-box;
  width: 100%;
  height: 60vh;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #303133;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre;
  overflow: auto;
  resize: vertical;
  outline: none;
}

.dashboard-design-json:focus {
  border-color: #409eff;
}

.dashboard-design-preview-body {
  padding: 0 8px 12px;
}

.dashboard-design-form-item {
  margin-bottom: 16px;
}

.dashboard-design-form-label {
  display: block;
  margin-bottom: 6px;
  color: #303133;
  font-size: 13px;
}
</style>
