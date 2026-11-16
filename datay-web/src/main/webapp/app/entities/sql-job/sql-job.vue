<template>
  <div class="sql-job-container">
    <div class="page-header">
      <div class="page-title-wrap">
        <h2 id="page-heading" data-cy="SqlJobHeading" class="page-title">SQL 任务</h2>
        <span class="page-subtitle">{{ sqlJobs.length }} 个任务</span>
      </div>
      <div class="page-header-actions">
        <el-button type="primary" id="jh-create-entity" data-cy="entityCreateButton" @click="startNewTask(selectedDirectoryId)">
          <font-awesome-icon icon="plus" />
          <span>新建 SQL 任务</span>
        </el-button>
      </div>
    </div>

    <div class="sql-job-layout">
      <div v-if="!treeCollapsed" class="tree-panel">
        <div class="tree-header">
          <h5>SQL 任务目录</h5>
          <div class="tree-actions">
            <el-button type="primary" size="small" @click="showAddDirectoryDialog(null)">
              <font-awesome-icon icon="plus" /> 目录
            </el-button>
          </div>
        </div>
        <div class="tree-search">
          <el-input v-model="treeSearch" placeholder="搜索任务名称" clearable>
            <template #prefix>
              <font-awesome-icon icon="search" />
            </template>
          </el-input>
        </div>
        <div class="tree-body" v-loading="directoryLoading || isFetching">
          <el-tree
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            :filter-node-method="filterNode"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="true"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span class="node-main">
                  <template v-if="data.type === 'directory'">
                    <font-awesome-icon icon="folder" class="tree-icon folder-icon" />
                    <span class="node-label">{{ node.label }}</span>
                  </template>
                  <template v-else>
                    <span class="status-dot" :class="statusClass(data.data.status)" :title="statusLabel(data.data.status)"></span>
                    <font-awesome-icon icon="file-code" class="tree-icon task-icon" />
                    <span class="node-label">{{ node.label }}</span>
                  </template>
                </span>
                <span class="tree-node-actions">
                  <template v-if="data.type === 'directory'">
                    <el-button link type="primary" size="small" title="新增子目录" @click.stop="showAddDirectoryDialog(data.id)">
                      <font-awesome-icon icon="plus" />
                    </el-button>
                    <el-button link type="primary" size="small" title="在该目录下新建任务" @click.stop="startNewTask(data.id)">
                      <font-awesome-icon icon="file-circle-plus" />
                    </el-button>
                    <el-button link type="primary" size="small" title="重命名" @click.stop="showEditDirectoryDialog(data.data)">
                      <font-awesome-icon icon="pen" />
                    </el-button>
                    <el-button
                      link
                      type="primary"
                      size="small"
                      title="上移"
                      :disabled="!canMoveDirectory(data.data, -1)"
                      @click.stop="moveDirectory(data.data, -1)"
                    >
                      <font-awesome-icon icon="arrow-up" />
                    </el-button>
                    <el-button
                      link
                      type="primary"
                      size="small"
                      title="下移"
                      :disabled="!canMoveDirectory(data.data, 1)"
                      @click.stop="moveDirectory(data.data, 1)"
                    >
                      <font-awesome-icon icon="arrow-down" />
                    </el-button>
                    <el-button link type="danger" size="small" title="删除目录" @click.stop="deleteDirectory(data.data)">
                      <font-awesome-icon icon="trash" />
                    </el-button>
                  </template>
                  <span v-else class="task-command-wrap" @click.stop>
                    <el-dropdown trigger="click" @command="command => handleTaskCommand(command, data.data)">
                      <el-button link type="primary" size="small" title="任务操作">
                        <font-awesome-icon icon="ellipsis-vertical" />
                      </el-button>
                      <template #dropdown>
                        <el-dropdown-menu>
                          <el-dropdown-item command="run"> 立即执行 </el-dropdown-item>
                          <el-dropdown-item v-if="data.data.status === 'OFFLINE'" command="online"> 上线 </el-dropdown-item>
                          <el-dropdown-item v-else command="offline"> 下线 </el-dropdown-item>
                          <el-dropdown-item command="move"> 移动到目录 </el-dropdown-item>
                          <el-dropdown-item command="delete" divided> 删除 </el-dropdown-item>
                        </el-dropdown-menu>
                      </template>
                    </el-dropdown>
                  </span>
                </span>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="!isFetching && treeData.length === 0" description="暂无 SQL 任务" :image-size="80" />
        </div>
        <el-tooltip content="收起目录树" placement="right">
          <button type="button" class="tree-floating-toggle tree-floating-collapse" @click="toggleTree">
            <font-awesome-icon icon="arrow-left" />
          </button>
        </el-tooltip>
      </div>

      <div class="editor-panel">
        <el-tooltip v-if="treeCollapsed" content="展开目录树" placement="right">
          <button type="button" class="tree-floating-toggle tree-floating-expand" @click="toggleTree">
            <font-awesome-icon icon="arrow-right" />
          </button>
        </el-tooltip>
        <div v-if="!editorVisible" class="empty-state">
          <font-awesome-icon icon="file-code" class="empty-icon" />
          <p>
            {{ selectedDirectoryId ? '请选择该目录下的 SQL 任务，或新建一个任务' : '请从左侧选择一个 SQL 任务，或新建一个任务' }}
          </p>
          <el-button type="primary" @click="startNewTask(selectedDirectoryId)">
            <font-awesome-icon icon="plus" />
            <span>新建 SQL 任务</span>
          </el-button>
        </div>
        <SqlJobEditor
          v-else
          :key="editorKey"
          :job-id="selectedJobId"
          :directory-id="newTaskDirectoryId"
          :directories="directories"
          @saved="onSaved"
        />
      </div>
    </div>

    <TaskOnlineModal
      ref="onlineModal"
      title="SQL 任务上线"
      :task-name="onlineTask?.jobName"
      :job-id="onlineTask?.id || ''"
      :cron="onlineTask?.cron"
      :retry-count="onlineTask?.retryCount ?? 0"
      :timeout-seconds="onlineTask?.timeoutSeconds ?? 0"
      :priority="onlineTask?.priority ?? 2"
      :loading="isOnlineSubmitting"
      @confirm="confirmOnline"
    />

    <app-modal v-model="deleteDialogVisible" id="removeEntity" title="确认删除" size="md">
      <div class="modal-body">
        <p data-cy="sqlJobDeleteDialogHeading">
          确定要删除 SQL 任务
          <strong>{{ removeJob?.jobName || removeJob?.id }}</strong>
          吗？
        </p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDeleteDialog">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeSqlJob"> 删除 </el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="directoryDialogVisible" :title="directoryDialogTitle" size="md">
      <div class="modal-body">
        <el-form name="directoryForm" label-position="top" @submit.prevent="saveDirectory">
          <el-form-item label="目录名称">
            <el-input v-model="directoryForm.name" placeholder="请输入目录名称" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="directoryForm.sortOrder" :min="0" :max="9999" :controls="false" />
          </el-form-item>
        </el-form>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="directoryDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveDirectory">确定</el-button>
        </div>
      </template>
    </app-modal>

    <app-modal v-model="moveDialogVisible" title="移动到目录" size="md">
      <div class="modal-body">
        <p>
          SQL 任务：<strong>{{ moveJob?.jobName }}</strong>
        </p>
        <el-form label-position="top">
          <el-form-item label="目标目录">
            <el-tree-select
              v-model="moveTargetDirectoryId"
              :data="directoryTreeOptions"
              :props="treeProps"
              node-key="id"
              check-strictly
              clearable
              placeholder="不选择表示根目录"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <p class="text-muted mb-0">清空选择后，任务将移动到根目录。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeMoveDialog">取消</el-button>
          <el-button type="primary" @click="confirmMove">确定</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./sql-job.component.ts"></script>

<style scoped>
.sql-job-container {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 12px;
  flex-wrap: wrap;
}

.page-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  font-size: 13px;
  color: #909399;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sql-job-layout {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}

.tree-panel {
  position: relative;
  width: 300px;
  min-width: 240px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.tree-floating-toggle {
  position: absolute;
  bottom: 16px;
  z-index: 10;
  width: 24px;
  height: 48px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  cursor: pointer;
  transition:
    color 0.2s,
    background 0.2s;
}

.tree-floating-collapse {
  right: 0;
  border-right: none;
  border-radius: 6px 0 0 6px;
  box-shadow: -2px 0 8px rgb(0 0 0 / 10%);
}

.tree-floating-expand {
  left: 0;
  border-left: none;
  border-radius: 0 6px 6px 0;
  box-shadow: 2px 0 8px rgb(0 0 0 / 10%);
}

.tree-floating-toggle:hover {
  color: #409eff;
  background: #ecf5ff;
}

.tree-header {
  padding: 10px 12px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h5 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.tree-actions {
  display: flex;
  gap: 8px;
}

.tree-search {
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
}

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 8px 64px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 4px;
  min-width: 0;
}

.node-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-icon {
  flex-shrink: 0;
}

.folder-icon {
  color: #e6a23c;
}

.task-icon {
  color: #409eff;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.status-online {
  background: #1f9d55;
}

.status-dot.status-offline {
  background: #c0c4cc;
}

.status-dot.status-running {
  background: #2f6fed;
}

.tree-node-actions {
  display: none;
  gap: 0;
  align-items: center;
  flex-shrink: 0;
}

.tree-node-actions :deep(.el-button) {
  height: 20px;
  margin-left: 0;
  padding: 0 2px;
}

.tree-node-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.custom-tree-node:hover .tree-node-actions {
  display: inline-flex;
}

.task-command-wrap {
  display: inline-flex;
  align-items: center;
  margin-left: 2px;
}

.editor-panel {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #909399;
  padding: 24px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #c0c4cc;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
}
</style>
