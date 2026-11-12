<template>
  <div class="dag-job-container">
    <h2
      id="page-heading"
      data-cy="DagJobHeading"
      class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap"
    >
      <span id="dag-job-heading">任务编排（DAG）</span>
      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新</span>
        </el-button>
        <router-link :to="createRouteLocation" custom v-slot="{ navigate }">
          <el-button
            type="primary"
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="jh-create-entity create-dag-job"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建编排</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div class="dag-job-layout">
      <div class="tree-panel">
        <div class="tree-header">
          <h5>编排目录</h5>
          <div class="tree-actions">
            <el-button type="primary" size="small" @click="showAddDirectoryDialog(null)">
              <font-awesome-icon icon="plus" /> 目录
            </el-button>
          </div>
        </div>
        <div class="tree-body" v-loading="directoryLoading">
          <div class="tree-shortcut" :class="{ active: !selectedDirectoryId }" @click="selectAllJobs">
            <font-awesome-icon icon="list" class="tree-icon" />
            <span>全部编排</span>
          </div>
          <el-tree
            ref="directoryTreeRef"
            :data="directoryTree"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="true"
            @node-click="handleDirectoryClick"
          >
            <template #default="{ node, data }">
              <span class="custom-tree-node">
                <span>
                  <font-awesome-icon icon="folder" class="tree-icon folder-icon" />
                  <span>{{ node.label }}</span>
                </span>
                <span class="tree-node-actions">
                  <el-button link type="primary" size="small" title="新增子目录" @click.stop="showAddDirectoryDialog(data.id)">
                    <font-awesome-icon icon="plus" />
                  </el-button>
                  <router-link :to="{ name: 'DagJobDesignNew', query: { directoryId: data.id } }" custom v-slot="{ navigate }">
                    <el-button link type="primary" size="small" title="在该目录下新建编排" @click.stop="navigate">
                      <font-awesome-icon icon="file-circle-plus" />
                    </el-button>
                  </router-link>
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
                </span>
              </span>
            </template>
          </el-tree>
        </div>
      </div>
      <div class="list-panel">
        <div class="alert alert-warning" v-if="!isFetching && dagJobs && dagJobs.length === 0">
          <span>尚未创建任务编排，点击「新建编排」开始设计 DAG。</span>
        </div>
        <div v-if="dagJobs && dagJobs.length > 0" class="list-page-scroll">
          <el-table :data="dagJobs" style="width: 100%" @sort-change="handleSortChange">
            <el-table-column prop="jobName" label="编排名称" sortable="custom" min-width="160"></el-table-column>
            <el-table-column prop="cron" label="Cron 表达式" sortable="custom" width="150">
              <template #default="scope">
                <span>{{ scope.row.cron || "未设置" }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" sortable="custom" width="110">
              <template #default="scope">
                <span :class="'status-badge status-' + (scope.row.status?.toLowerCase() || '')">
                  {{ scope.row.status || "OFFLINE" }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" sortable="custom" width="90">
              <template #default="scope">
                <el-tag
                  size="small"
                  :type="scope.row.priority === 3 ? 'danger' : scope.row.priority === 1 ? 'info' : 'primary'"
                >
                  {{ scope.row.priority === 3 ? "高" : scope.row.priority === 1 ? "低" : "中" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" sortable="custom" width="160">
              <template #default="scope">
                {{ formatDateShort(scope.row.updateTime) || "" }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" sortable="custom" width="160">
              <template #default="scope">
                {{ formatDateShort(scope.row.createTime) || "" }}
              </template>
            </el-table-column>
            <el-table-column label="所属目录" min-width="180" show-overflow-tooltip>
              <template #default="scope">
                {{ directoryPath(scope.row) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" min-width="400">
              <template #default="scope">
                <div class="btn-group">
                  <router-link :to="{ name: 'DagJobDesign', params: { jobId: scope.row.id } }" custom v-slot="{ navigate }">
                    <el-button type="primary" size="small" @click="navigate" data-cy="entityDesignButton">
                      <span class="d-none d-md-inline">设计</span>
                    </el-button>
                  </router-link>
                  <el-button type="success" size="small" @click="handleExecuteOnce(scope.row)" data-cy="entityRunButton">
                    <span class="d-none d-md-inline">立即执行</span>
                  </el-button>
                  <el-button v-if="scope.row.status === 'OFFLINE'" type="warning" size="small" @click="handleOnline(scope.row)">
                    <span class="d-none d-md-inline">上线</span>
                  </el-button>
                  <el-button v-if="scope.row.status !== 'OFFLINE'" type="info" size="small" @click="handleOffline(scope.row)">
                    <span class="d-none d-md-inline">下线</span>
                  </el-button>
                  <el-dropdown trigger="click" @command="command => handleMoreCommand(command, scope.row)">
                    <el-button type="info" plain size="small">
                      <span class="d-none d-md-inline">更多</span>
                      <font-awesome-icon icon="ellipsis-vertical"></font-awesome-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="move">移动到目录</el-dropdown-item>
                        <el-dropdown-item command="delete" divided data-cy="entityDeleteButton">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-show="dagJobs && dagJobs.length > 0">
          <div class="list-pagination">
            <jhi-item-count :page="page" :total="queryCount" :items-per-page="itemsPerPage"></jhi-item-count>
            <el-pagination
              background
              layout="sizes, prev, pager, next, jumper"
              :total="totalItems"
              :page-sizes="[10, 20, 50, 100]"
              :pager-count="7"
              v-model:current-page="page"
              v-model:page-size="itemsPerPage"
            />
          </div>
        </div>
      </div>
    </div>
    <TaskOnlineModal
      ref="onlineModal"
      title="任务编排上线"
      :task-name="onlineTask?.jobName"
      :cron="onlineTask?.cron"
      :retry-count="onlineTask?.retryCount ?? 0"
      :timeout-seconds="onlineTask?.timeoutSeconds ?? 0"
      :priority="onlineTask?.priority ?? 2"
      :loading="isOnlineSubmitting"
      @confirm="confirmOnline"
    />
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="dagJobDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除任务编排 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeDagJob()">删除</el-button>
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
          编排：<strong>{{ moveJob?.jobName }}</strong>
        </p>
        <el-form label-position="top">
          <el-form-item label="目标目录">
            <el-tree-select
              v-model="moveTargetDirectoryId"
              :data="directoryTree"
              :props="treeProps"
              node-key="id"
              check-strictly
              clearable
              placeholder="不选择表示根目录"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <p class="text-muted mb-0">清空选择后，编排将移动到根目录。</p>
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

<script lang="ts" src="./dag-job.component.ts"></script>

<style scoped>
.dag-job-container {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.dag-job-layout {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
}

.tree-panel {
  width: 280px;
  min-width: 240px;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  background: #fff;
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

.tree-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.tree-shortcut {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 4px;
  border-radius: 4px;
  font-size: 14px;
  color: #303133;
  cursor: pointer;
}

.tree-shortcut:hover {
  background: #f5f7fa;
}

.tree-shortcut.active {
  background: #ecf5ff;
  color: #409eff;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.tree-icon {
  margin-right: 6px;
}

.folder-icon {
  color: #e6a23c;
}

.tree-node-actions {
  display: none;
  gap: 0;
  align-items: center;
}

.tree-node-actions :deep(.el-button) {
  height: 20px;
  margin-left: 0;
  padding: 0 2px;
}

.tree-node-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.tree-node-actions :deep(a) {
  display: inline-flex;
  margin: 0;
}

.custom-tree-node:hover .tree-node-actions {
  display: inline-flex;
}

.list-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-left: 12px;
}

.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
}

.status-online {
  color: #1f9d55;
  background-color: #e3f7ec;
}

.status-offline {
  color: #909399;
  background-color: #f0f2f5;
}

.status-running {
  color: #2f6fed;
  background-color: #e8f0fe;
}
</style>
