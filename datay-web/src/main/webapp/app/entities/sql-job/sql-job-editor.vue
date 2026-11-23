<template>
  <div class="data-query-container" :class="{ standalone }">
    <div class="query-header">
      <div v-if="standalone" class="header-left">
        <router-link :to="{ name: 'SqlJob' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
      </div>
      <div class="header-center">
        <div class="form-item">
          <label class="field-label">任务名称</label>
          <el-input v-model="sqlJob.jobName" placeholder="请输入任务名称" />
        </div>
        <div class="form-item">
          <label class="field-label">所属目录</label>
          <el-tree-select
            v-model="sqlJob.directoryId"
            :data="directoryTreeOptions"
            :props="{ label: 'label', children: 'children' }"
            node-key="id"
            check-strictly
            clearable
            placeholder="根目录"
            class="dir-select"
          />
        </div>
        <div class="form-item schedule-info">
          <label class="field-label">调度信息</label>
          <div class="schedule-info-content">
            <el-tag size="small" :type="scheduleStatusType" effect="light">{{ scheduleStatusLabel }}</el-tag>
            <el-tag v-if="sqlJob.cron" size="small" effect="plain" class="schedule-cron">{{ sqlJob.cron }}</el-tag>
            <span v-else class="schedule-muted">未配置 Cron</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-tooltip content="AI 助手：用自然语言生成 SQL" placement="bottom">
          <el-button @click="openAiDrawer" class="ai-entry-btn">
            <font-awesome-icon icon="wand-magic-sparkles" class="mr-1" />
            <span>AI 助手</span>
          </el-button>
        </el-tooltip>
        <el-button type="primary" :loading="isSaving" @click="save">保存</el-button>
      </div>
    </div>

    <div class="query-body">
      <div class="query-sidebar">
        <div class="sidebar-header">
          <span>数据源</span>
        </div>
        <div class="sidebar-select">
          <el-select
            :model-value="dataSourceId"
            placeholder="请选择数据源"
            style="width: 100%"
            filterable
            @update:model-value="onDataSourceChange"
          >
            <el-option v-for="ds in dataSources" :key="ds.id" :label="`${ds.name}（${ds.type}）`" :value="ds.id" />
          </el-select>
        </div>
        <div class="sidebar-header">
          <span>数据库</span>
        </div>
        <div class="sidebar-content" v-loading="treeLoading">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="{ label: 'label', children: 'children', isLeaf: 'isLeaf' }"
            node-key="id"
            lazy
            :load="loadTables"
            highlight-current
            class="schema-tree"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node" :class="{ 'is-table': data?.type === 'table' }" @dblclick.stop="handleNodeDblClick(data)">
                <font-awesome-icon :icon="data?.type === 'schema' ? 'database' : 'table'" class="node-icon" />
                <span class="node-label">{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="!treeLoading && dataSourceId && treeData.length === 0" description="暂无数据库" />
          <el-empty v-if="!dataSourceId" description="请选择数据源" />
        </div>
      </div>

      <div ref="queryMainRef" class="query-main">
        <div class="sql-editor-section" :style="{ height: editorHeight + 'px' }">
          <div class="section-header">
            <div class="section-actions">
              <el-button size="small" text :loading="isExecuting" @click="executeDebug">
                <font-awesome-icon icon="play" />
                <span>调试运行</span>
              </el-button>
              <el-button size="small" text @click="formatSql" class="format-btn" title="格式化 SQL (Ctrl+Shift+F)">
                <font-awesome-icon icon="indent" />
                <span>格式化</span>
              </el-button>
            </div>
            <span class="shortcut-hint" :class="{ 'has-selection': hasSelection }">
              {{ hasSelection ? '已选中，执行选中部分' : 'Ctrl+Space 提示 · Ctrl+Enter 调试运行 · Ctrl+Shift+F 格式化' }}
            </span>
          </div>
          <div ref="editorContainer" class="sql-editor"></div>
        </div>

        <div class="resizer" :class="{ dragging: isDragging }" @mousedown="startResize">
          <div class="resizer-handle"></div>
        </div>

        <div class="result-section">
          <el-tabs v-model="activeResultTab" class="result-tabs">
            <el-tab-pane label="查询结果" name="result">
              <div class="result-content">
                <el-table v-if="resultColumns.length > 0" :data="resultData" stripe border style="width: 100%" height="100%" size="small">
                  <el-table-column v-for="col in resultColumns" :key="col" :prop="col" :label="col" show-overflow-tooltip min-width="120" />
                  <template #empty>
                    <el-empty description="执行成功，无数据返回" />
                  </template>
                </el-table>
                <el-empty v-else :description="resultPlaceholder" />
              </div>
            </el-tab-pane>
            <el-tab-pane label="执行日志" name="log">
              <div class="result-content log-content">
                <template v-if="hasExecuted">
                  <div class="log-item">
                    <span class="log-label">执行状态</span>
                    <el-tag size="small" :type="executionStatusType" effect="light">
                      {{ executionStatusLabel }}
                    </el-tag>
                  </div>
                  <div class="log-item">
                    <span class="log-label">耗时</span>
                    <span class="log-value">{{ executionTime !== null ? formatTime(executionTime) : '-' }}</span>
                  </div>
                  <div class="log-item">
                    <span class="log-label">影响行数</span>
                    <span class="log-value">{{ affectedRows !== null ? affectedRows : '-' }}</span>
                  </div>
                  <div class="log-block">
                    <span class="log-label">执行 SQL</span>
                    <pre class="log-sql">{{ executedSql }}</pre>
                  </div>
                  <div v-if="queryError" class="log-error">
                    <font-awesome-icon icon="exclamation-circle" />
                    <span>{{ queryError }}</span>
                  </div>
                </template>
                <el-empty v-else description="暂无执行日志" />
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>

    <data-query-ai v-model="aiDrawerVisible" :data-source-id="dataSourceId" assistant-id="sql-task" @apply-sql="applyAiSql" />
  </div>
</template>

<script lang="ts" src="./sql-job-editor.component.ts"></script>

<style scoped>
.data-query-container {
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #fff);
}

.data-query-container.standalone {
  height: calc(100vh - var(--app-topbar-height, 88px));
}

.query-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-regular, #606266);
  text-decoration: none;
  cursor: pointer;
  font-size: 14px;
}

.back-link:hover {
  color: var(--el-color-primary, #409eff);
}

.header-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 16px;
  min-width: 0;
  flex-wrap: wrap;
}

.form-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.field-label {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
  margin: 0;
}

.dir-select {
  width: 200px;
}

.schedule-info {
  flex-shrink: 0;
  white-space: nowrap;
}

.schedule-info-content {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  white-space: nowrap;
}

.schedule-cron {
  font-family: var(--el-font-family-monospace, monospace);
  white-space: nowrap;
}

.schedule-muted {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.query-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

.query-sidebar {
  width: 260px;
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  background: var(--el-bg-color, #fff);
}

.sidebar-header {
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
}

.sidebar-select {
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.sidebar-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.schema-tree {
  background: transparent;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  padding: 2px 0;
}

.custom-tree-node.is-table {
  color: var(--el-text-color-regular, #606266);
}

.node-icon {
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.query-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.sql-editor-section {
  display: flex;
  flex-direction: column;
  min-height: 80px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary, #303133);
  background: var(--el-fill-color-lighter, #f5f7fa);
  flex-shrink: 0;
  gap: 12px;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shortcut-hint {
  font-weight: normal;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
  transition: color 0.2s ease;
}

.shortcut-hint.has-selection {
  color: var(--el-color-primary, #409eff);
  font-weight: 500;
}

.sql-editor {
  flex: 1;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 4px;
  min-height: 0;
}

.sql-editor :deep(.cm-editor) {
  height: 100%;
  font-size: 13px;
}

.sql-editor :deep(.cm-scroller) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}

.sql-editor :deep(.cm-gutters) {
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.result-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 80px;
}

.result-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.result-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  flex-shrink: 0;
}

.result-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.result-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.result-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.resizer {
  height: 6px;
  cursor: row-resize;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-border-color-lighter, #e4e7ed);
  transition: background 0.2s;
}

.resizer:hover,
.resizer.dragging {
  background: var(--el-color-primary, #409eff);
}

.resizer-handle {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: var(--el-text-color-placeholder, #a8abb2);
  transition: background 0.2s;
}

.resizer:hover .resizer-handle,
.resizer.dragging .resizer-handle {
  background: #fff;
}

.resizer.dragging {
  user-select: none;
}

.log-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
}

.log-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  flex-shrink: 0;
}

.log-label {
  width: 64px;
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.log-value {
  color: var(--el-text-color-primary, #303133);
}

.log-block {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex-shrink: 0;
  font-size: 13px;
}

.log-sql {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 8px 12px;
  max-height: 200px;
  overflow: auto;
  border-radius: 4px;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-color-danger, #f56c6c);
  background: var(--el-color-danger-light-9, #fef0f0);
  white-space: pre-wrap;
  word-break: break-all;
  flex-shrink: 0;
}

.result-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
  min-height: 0;
}

.mr-1 {
  margin-right: 4px;
}

.ai-entry-btn {
  color: var(--el-color-primary, #409eff);
}
</style>
