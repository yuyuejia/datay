<template>
  <div class="data-query-container">
    <div class="query-header">
      <div class="header-left">
        <router-link :to="{ name: 'SqlJob' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="query-title">
          {{ isEditMode ? "编辑 SQL 任务" : "新建 SQL 任务" }}
          <span v-if="dataSourceName" class="ds-name"
            >({{ dataSourceName }})</span
          >
        </h3>
      </div>
      <div class="header-center">
        <div class="field-item">
          <span class="field-label">任务名称</span>
          <el-input
            v-model="sqlJob.jobName"
            placeholder="请输入任务名称"
            class="field-input"
          />
        </div>
        <div class="field-item">
          <span class="field-label">Cron 表达式</span>
          <el-input
            v-model="sqlJob.cron"
            placeholder="可选"
            class="field-input"
          />
        </div>
      </div>
      <div class="header-right">
        <span class="execute-info" v-if="executionTime !== null">
          <font-awesome-icon icon="clock" />
          <span>耗时: {{ formatTime(executionTime) }}</span>
          <span v-if="affectedRows !== null"> | 行数: {{ affectedRows }}</span>
        </span>
        <el-button :loading="isExecuting" @click="executeDebug">
          <font-awesome-icon icon="play" class="mr-1" />
          <span>调试运行</span>
        </el-button>
        <el-button type="primary" :loading="isSaving" @click="save"
          >保存</el-button
        >
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
            <el-option
              v-for="ds in dataSources"
              :key="ds.id"
              :label="`${ds.name}（${ds.type}）`"
              :value="ds.id"
            />
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
              <span
                class="custom-tree-node"
                :class="{ 'is-table': data?.type === 'table' }"
                @dblclick.stop="handleNodeDblClick(data)"
              >
                <font-awesome-icon
                  :icon="data?.type === 'schema' ? 'database' : 'table'"
                  class="node-icon"
                />
                <span class="node-label">{{ node.label }}</span>
              </span>
            </template>
          </el-tree>
          <el-empty
            v-if="!treeLoading && dataSourceId && treeData.length === 0"
            description="暂无数据库"
          />
          <el-empty v-if="!dataSourceId" description="请选择数据源" />
        </div>
      </div>

      <div class="query-main">
        <div
          class="sql-editor-section"
          :style="{ height: editorHeight + 'px' }"
        >
          <div class="section-header">
            <div class="section-actions">
              <el-button
                size="small"
                text
                @click="formatSql"
                class="format-btn"
                title="格式化 SQL (Ctrl+Shift+F)"
              >
                <font-awesome-icon icon="indent" />
                <span>格式化</span>
              </el-button>
            </div>
            <span
              class="shortcut-hint"
              :class="{ 'has-selection': hasSelection }"
            >
              {{
                hasSelection
                  ? "已选中，执行选中部分"
                  : "Ctrl+Space 提示 · Ctrl+Enter 调试运行 · Ctrl+Shift+F 格式化"
              }}
            </span>
          </div>
          <div ref="editorContainer" class="sql-editor"></div>
        </div>

        <div
          class="resizer"
          :class="{ dragging: isDragging }"
          @mousedown="startResize"
        >
          <div class="resizer-handle"></div>
        </div>

        <div class="result-section">
          <div class="section-header">
            <span>调试结果</span>
            <span v-if="queryError" class="error-tag">
              <font-awesome-icon icon="exclamation-circle" />
              <span>{{ queryError }}</span>
            </span>
          </div>
          <div class="result-content">
            <el-table
              v-if="resultColumns.length > 0"
              :data="resultData"
              stripe
              border
              style="width: 100%"
              height="100%"
              size="small"
            >
              <el-table-column
                v-for="col in resultColumns"
                :key="col"
                :prop="col"
                :label="col"
                show-overflow-tooltip
                min-width="120"
              />
              <template #empty>
                <el-empty description="执行成功，无数据返回" />
              </template>
            </el-table>
            <el-empty v-else description="点击「调试运行」执行 SQL" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./sql-job-update.component.ts"></script>

<style scoped>
.data-query-container {
  height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color, #fff);
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

.query-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.ds-name {
  font-weight: normal;
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
}

.header-center {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-width: 0;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-label {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
}

.field-input {
  width: 200px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.execute-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}

.query-body {
  flex: 1;
  display: flex;
  overflow: hidden;
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
  font-family: "Menlo", "Monaco", "Courier New", monospace;
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

.error-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-color-danger, #f56c6c);
  font-weight: normal;
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
</style>
