<template>
  <div class="data-query-container">
    <div class="query-header">
      <div class="header-left">
        <router-link :to="{ name: 'DataSource' }" class="back-link">
          <font-awesome-icon icon="arrow-left" />
          <span>返回</span>
        </router-link>
        <h3 class="query-title">数据查询 <span v-if="dataSourceName" class="ds-name">({{ dataSourceName }})</span></h3>
      </div>
      <div class="header-right">
        <span class="execute-info" v-if="executionTime !== null">
          <font-awesome-icon icon="clock" />
          <span>耗时: {{ formatTime(executionTime) }}</span>
          <span v-if="affectedRows !== null"> | 行数: {{ affectedRows }}</span>
        </span>
      </div>
    </div>

    <div class="query-body">
      <div class="query-sidebar">
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
          >
            <template #default="{ node, data }">
              <span
                class="custom-tree-node"
                :class="{ 'is-table': data?.type === 'table' }"
                @dblclick.stop="handleNodeDblClick(data)"
                @contextmenu.stop.prevent="openContextMenu($event, data)"
              >
                <font-awesome-icon :icon="data?.type === 'schema' ? 'database' : 'table'" class="node-icon" />
                <span class="node-label">{{ node.label }}</span>
                <span v-if="data?.type === 'table'" class="node-actions" @click.stop>
                  <el-tooltip content="预览数据" placement="top">
                    <button class="node-action-btn" @click.stop="previewTable(data)">
                      <font-awesome-icon icon="eye" />
                    </button>
                  </el-tooltip>
                  <el-tooltip content="更多操作" placement="top">
                    <button class="node-action-btn" @click.stop="openContextMenu($event, data)">
                      <font-awesome-icon icon="ellipsis-vertical" />
                    </button>
                  </el-tooltip>
                </span>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="!treeLoading && treeData.length === 0" description="暂无数据库" />
        </div>
      </div>

      <div class="query-main">
        <el-tabs v-model="activeTab" type="card" class="query-tabs" @tab-remove="handleTabRemove">
          <el-tab-pane name="sql" :closable="false">
            <template #label>
              <span class="tab-label">
                <font-awesome-icon icon="code" />
                <span>SQL 查询</span>
              </span>
            </template>
            <div class="sql-pane">
              <div class="sql-editor-section" :style="{ height: editorHeight + 'px' }">
                <div class="section-header">
                  <div class="section-actions">
                    <el-button type="primary" size="small" text :loading="isExecuting" @click="executeQuery">
                      <font-awesome-icon icon="play" class="mr-1" />
                      <span>执行</span>
                    </el-button>
                    <el-button size="small" text @click="formatSql" class="format-btn" title="格式化 SQL (Ctrl+Shift+F)">
                      <font-awesome-icon icon="indent" />
                      <span>格式化</span>
                    </el-button>
                    <el-tooltip content="AI 助手：用自然语言生成 SQL" placement="bottom">
                      <el-button size="small" text @click="aiDrawerVisible = true" class="ai-entry-btn">
                        <font-awesome-icon icon="wand-magic-sparkles" class="mr-1" />
                        <span>AI 助手</span>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <span class="shortcut-hint" :class="{ 'has-selection': hasSelection }">
                    {{ hasSelection ? '已选中，执行选中部分' : 'Ctrl+Space 提示 · Ctrl+Enter 执行 · Ctrl+Shift+F 格式化' }}
                  </span>
                </div>
                <div ref="editorContainer" class="sql-editor"></div>
              </div>

              <div class="resizer" :class="{ dragging: isDragging }" @mousedown="startResize">
                <div class="resizer-handle"></div>
              </div>

              <div class="result-section">
                <div class="section-header">
                  <span>查询结果</span>
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
                      <el-empty description="查询成功，无数据返回" />
                    </template>
                  </el-table>
                  <el-empty v-else description="请执行 SQL 查询" />
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane v-for="tab in previewTabs" :key="tab.key" :name="tab.key" closable>
            <template #label>
              <span class="tab-label">
                <font-awesome-icon icon="table" />
                <span>{{ tab.table }}</span>
              </span>
            </template>
            <div class="preview-pane">
              <div class="preview-toolbar">
                <div class="preview-meta">
                  <span class="preview-table-name">{{ tab.catalog ? tab.catalog + '.' : '' }}{{ tab.schema }}.{{ tab.table }}</span>
                  <span v-if="tab.metaLoading || tab.loading" class="preview-status">加载中…</span>
                  <span v-else-if="tab.metaError" class="error-tag">
                    <font-awesome-icon icon="exclamation-circle" />
                    <span>{{ tab.metaError }}</span>
                  </span>
                  <span v-else class="preview-status">{{ tab.rows.length }} 行 · {{ tab.columns.length }} 列</span>
                </div>
                <div class="preview-actions">
                  <el-select v-model="tab.limit" size="small" class="limit-select" @change="loadPreview(tab)">
                    <el-option :value="10" label="10 行" />
                    <el-option :value="100" label="100 行" />
                    <el-option :value="500" label="500 行" />
                    <el-option :value="1000" label="1000 行" />
                  </el-select>
                  <el-button size="small" text :loading="tab.metaLoading || tab.loading" @click="refreshPreview(tab)">
                    <font-awesome-icon icon="sync" />
                    <span>刷新</span>
                  </el-button>
                  <el-button size="small" text @click="copyPreviewSelect(tab)">
                    <font-awesome-icon icon="copy" />
                    <span>复制 SELECT</span>
                  </el-button>
                  <el-button size="small" text @click="openSelectInEditor(tab)">
                    <font-awesome-icon icon="code" />
                    <span>在查询页打开</span>
                  </el-button>
                </div>
              </div>

              <div class="preview-basic" v-loading="tab.metaLoading">
                <el-descriptions v-if="tab.detail" :column="3" border size="small">
                  <el-descriptions-item label="表名">{{ tab.detail.table || tab.table }}</el-descriptions-item>
                  <el-descriptions-item label="Schema">{{ tab.detail.schema || tab.schema || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="目录">{{ tab.detail.catalog || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="数据库类型">{{ tab.detail.dbType || dataSourceType || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="字段数">{{ tab.detail.columns?.length ?? 0 }}</el-descriptions-item>
                  <el-descriptions-item label="索引数">{{ tab.detail.indexes?.length ?? 0 }}</el-descriptions-item>
                  <el-descriptions-item label="表注释" :span="3">{{ tab.detail.comment || '-' }}</el-descriptions-item>
                </el-descriptions>
                <span v-else-if="tab.metaError" class="error-tag">
                  <font-awesome-icon icon="exclamation-circle" />
                  <span>{{ tab.metaError }}</span>
                </span>
                <span v-else-if="!tab.metaLoading" class="preview-status">暂无表信息</span>
              </div>

              <el-tabs v-model="tab.subTab" class="preview-subtabs">
                <el-tab-pane label="字段信息" name="columns">
                  <div class="preview-section" v-loading="tab.metaLoading">
                    <el-table :data="tab.detail?.columns || []" stripe border size="small" style="width: 100%">
                      <el-table-column prop="name" label="字段名" min-width="140" show-overflow-tooltip />
                      <el-table-column label="类型" min-width="120" show-overflow-tooltip>
                        <template #default="{ row }">{{ formatColumnType(row) }}</template>
                      </el-table-column>
                      <el-table-column label="可空" width="70" align="center">
                        <template #default="{ row }">{{ row.nullable === false ? '否' : '是' }}</template>
                      </el-table-column>
                      <el-table-column label="主键" width="70" align="center">
                        <template #default="{ row }">
                          <font-awesome-icon v-if="row.primaryKey" icon="check" class="pk-icon" />
                          <span v-else>-</span>
                        </template>
                      </el-table-column>
                      <el-table-column prop="defaultValue" label="默认值" min-width="100" show-overflow-tooltip />
                      <el-table-column prop="comment" label="注释" min-width="140" show-overflow-tooltip />
                      <template #empty>
                        <el-empty description="暂无字段信息" />
                      </template>
                    </el-table>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="索引信息" name="indexes">
                  <div class="preview-section" v-loading="tab.metaLoading">
                    <el-table :data="tab.detail?.indexes || []" stripe border size="small" style="width: 100%">
                      <el-table-column prop="name" label="索引名" min-width="160" show-overflow-tooltip />
                      <el-table-column label="唯一" width="70" align="center">
                        <template #default="{ row }">{{ row.unique ? '是' : '否' }}</template>
                      </el-table-column>
                      <el-table-column prop="type" label="类型" width="100" />
                      <el-table-column label="字段" min-width="200" show-overflow-tooltip>
                        <template #default="{ row }">{{ (row.columns || []).join(', ') }}</template>
                      </el-table-column>
                      <template #empty>
                        <el-empty description="暂无索引信息" />
                      </template>
                    </el-table>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="数据预览" name="data">
                  <div class="preview-data" v-loading="tab.loading">
                    <el-table
                      v-if="tab.columns.length > 0"
                      :data="tab.rows"
                      stripe
                      border
                      style="width: 100%"
                      height="100%"
                      size="small"
                    >
                      <el-table-column
                        v-for="col in tab.columns"
                        :key="col"
                        :prop="col"
                        :label="col"
                        show-overflow-tooltip
                        min-width="140"
                      />
                      <template #empty>
                        <el-empty description="查询成功，无数据返回" />
                      </template>
                    </el-table>
                    <el-empty v-else-if="!tab.loading" :description="tab.error ? '数据加载失败' : '暂无数据'" />
                  </div>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <data-query-ai
      v-model="aiDrawerVisible"
      :data-source-id="dataSourceId"
      assistant-id="query"
      @apply-sql="applyAiSql"
    />

    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="table-context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
        @contextmenu.stop.prevent
      >
        <div class="ctx-header" :title="contextMenu.data?.name">{{ contextMenu.data?.name }}</div>
        <div class="ctx-item" @click="handleTableCommand('preview', contextMenu.data)">
          <font-awesome-icon icon="eye" />
          <span>预览数据</span>
        </div>
        <div class="ctx-divider"></div>
        <div class="ctx-item" @click="handleTableCommand('select', contextMenu.data)">
          <font-awesome-icon icon="code" />
          <span>复制 SELECT</span>
        </div>
        <div class="ctx-item" @click="handleTableCommand('insert', contextMenu.data)">
          <font-awesome-icon icon="pencil-alt" />
          <span>复制 INSERT</span>
        </div>
        <div class="ctx-item" @click="handleTableCommand('ddl', contextMenu.data)">
          <font-awesome-icon icon="file-code" />
          <span>复制 CREATE TABLE</span>
        </div>
        <div class="ctx-divider"></div>
        <div class="ctx-item" @click="handleTableCommand('toEditor', contextMenu.data)">
          <font-awesome-icon icon="arrow-right-to-bracket" />
          <span>在 SQL 编辑器中打开</span>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script lang="ts" src="./data-query.component.ts"></script>

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

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
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
  min-width: 0;
}

.custom-tree-node.is-table {
  color: var(--el-text-color-regular, #606266);
}

.node-icon {
  font-size: 12px;
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.node-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.custom-tree-node.is-table:hover .node-actions {
  display: inline-flex;
}

.node-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.node-action-btn:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.query-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.query-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.query-tabs :deep(.el-tabs__header) {
  margin: 0;
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

.query-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.query-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.tab-label .fa-icon {
  font-size: 12px;
}

.sql-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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
  gap: 4px;
}

.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  font-weight: normal;
}

.result-content {
  flex: 1;
  overflow: auto;
  padding: 8px;
  min-height: 0;
}

.preview-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 6px 12px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  overflow: hidden;
}

.preview-table-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-status {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  flex-shrink: 0;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.limit-select {
  width: 96px;
}

.preview-basic {
  flex-shrink: 0;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  max-height: 40%;
  overflow: auto;
  box-sizing: border-box;
}

.preview-subtabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.preview-subtabs :deep(.el-tabs__header) {
  margin: 0;
  flex-shrink: 0;
  padding: 0 8px;
}

.preview-subtabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.preview-subtabs :deep(.el-tab-pane) {
  height: 100%;
}

.preview-section {
  height: 100%;
  overflow: auto;
  padding: 12px;
  box-sizing: border-box;
}

.preview-data {
  height: 100%;
  overflow: auto;
  padding: 8px;
  box-sizing: border-box;
}

.pk-icon {
  color: var(--el-color-warning, #e6a23c);
}

.mr-1 {
  margin-right: 4px;
}

.ai-entry-btn {
  color: var(--el-color-primary, #409eff);
}

.ai-toolbar-btn {
  color: var(--el-color-primary, #409eff);
}
</style>

<style>
.table-context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 200px;
  padding: 4px;
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  font-size: 13px;
}

.table-context-menu .ctx-header {
  padding: 4px 10px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary, #909399);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-context-menu .ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 4px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
  transition: background 0.15s;
}

.table-context-menu .ctx-item:hover {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.table-context-menu .ctx-item .fa-icon {
  width: 14px;
  font-size: 12px;
}

.table-context-menu .ctx-divider {
  height: 1px;
  margin: 4px 0;
  background: var(--el-border-color-lighter, #e4e7ed);
}
</style>
