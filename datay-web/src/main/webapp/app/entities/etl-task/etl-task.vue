<template>
  <div class="etl-task-container">
    <h2 id="page-heading" data-cy="ETLTaskHeading" class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap">
      <span id="etl-task-heading">{{ pageTitle }}</span>
      <div class="d-flex align-items-center">
        <el-input class="mr-2" style="width: 280px" v-model="search" placeholder="按任务名称 / 描述搜索" clearable />
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>刷新</span>
        </el-button>
        <el-button type="success" class="mr-2" @click="triggerImport" data-cy="entityImportButton">
          <font-awesome-icon icon="file-import"></font-awesome-icon>
          <span>导入任务</span>
        </el-button>
        <input ref="fileInput" type="file" accept=".json,application/json" style="display: none" @change="onFileSelected" />
        <router-link :to="createRouteLocation" custom v-slot="{ navigate }">
          <el-button
            type="primary"
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="jh-create-entity create-etl-task"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>{{ createButtonText }}</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div class="etl-task-layout">
      <div class="tree-panel">
        <div class="tree-header">
          <h5>{{ pageTitle }}目录</h5>
          <div class="tree-actions">
            <el-button type="primary" size="small" @click="showAddDirectoryDialog(null)">
              <font-awesome-icon icon="plus" /> 目录
            </el-button>
          </div>
        </div>
        <div class="tree-body" v-loading="directoryLoading">
          <div class="tree-shortcut" :class="{ active: !selectedDirectoryId }" @click="selectAllTasks">
            <font-awesome-icon icon="list" class="tree-icon" />
            <span>全部任务</span>
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
                  <router-link :to="{ name: createRouteName, query: { directoryId: data.id } }" custom v-slot="{ navigate }">
                    <el-button link type="primary" size="small" title="在该目录下新建任务" @click.stop="navigate">
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
        <div class="alert alert-warning" v-if="!isFetching && eTLTasks && eTLTasks.length === 0">
          <span>未找到{{ pageTitle }}</span>
        </div>
        <div v-if="eTLTasks && eTLTasks.length > 0" class="list-page-scroll">
          <el-table
            :data="eTLTasks"
            style="width: 100%"
            :default-sort="{ prop: 'updateTime', order: 'descending' }"
            @sort-change="handleSortChange"
          >
            <el-table-column prop="taskName" label="任务名称" sortable="custom" width="300"></el-table-column>
            <el-table-column prop="taskDesc" label="任务描述" sortable="custom" width="300" show-overflow-tooltip></el-table-column>
            <el-table-column v-if="!isRealtimeTask" prop="cron" label="Cron 表达式" sortable="custom" width="130"></el-table-column>
            <el-table-column prop="status" label="状态" :sortable="isRealtimeTask ? false : 'custom'" width="100">
              <template #default="scope">
                <span :class="rowStatusClass(scope.row)">{{ rowStatus(scope.row) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isRealtimeTask" prop="priority" label="优先级" sortable="custom" width="90">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.priority === 3 ? 'danger' : scope.row.priority === 1 ? 'info' : 'primary'">
                  {{ scope.row.priority === 3 ? '高' : scope.row.priority === 1 ? '低' : '中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" sortable="custom" width="160">
              <template #default="scope">
                {{ formatDateShort(scope.row.updateTime) || '' }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" sortable="custom" width="160">
              <template #default="scope">
                {{ formatDateShort(scope.row.createTime) || '' }}
              </template>
            </el-table-column>
            <el-table-column label="所属目录" min-width="180" show-overflow-tooltip>
              <template #default="scope">
                {{ directoryPath(scope.row) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" min-width="360">
              <template #default="scope">
                <div class="btn-group">
                  <router-link :to="{ name: editRouteName, params: { eTLTaskId: scope.row.id } }" custom v-slot="{ navigate }">
                    <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                      <span class="d-none d-md-inline">编辑</span>
                    </el-button>
                  </router-link>
                  <el-button type="success" size="small" @click="runETLTask(scope.row.id)" class="run" data-cy="entityRunButton">
                    <span class="d-none d-md-inline">立即执行</span>
                  </el-button>
                  <el-button
                    v-if="isRealtimeTask"
                    type="danger"
                    size="small"
                    :loading="terminatingTaskId === scope.row.id"
                    :disabled="!!terminatingTaskId && terminatingTaskId !== scope.row.id"
                    @click="terminateETLTask(scope.row)"
                    class="terminate"
                    data-cy="entityTerminateButton"
                  >
                    <span class="d-none d-md-inline">终止</span>
                  </el-button>
                  <el-button
                    type="warning"
                    size="small"
                    v-if="!isRealtimeTask && scope.row.status === 'OFFLINE'"
                    @click="onlineETLTask(scope.row)"
                    class="online"
                    data-cy="entityOnlineButton"
                  >
                    <span class="d-none d-md-inline">上线</span>
                  </el-button>
                  <el-button
                    size="small"
                    v-if="scope.row.status != 'OFFLINE'"
                    @click="offlineETLTask(scope.row)"
                    class="offline"
                    data-cy="entityOfflineButton"
                  >
                    <span class="d-none d-md-inline">下线</span>
                  </el-button>
                  <el-dropdown trigger="click" @command="command => handleRowCommand(command, scope.row)">
                    <el-button type="info" plain size="small" data-cy="entityMoreButton">
                      <span class="d-none d-md-inline">更多</span>
                      <font-awesome-icon icon="ellipsis-vertical"></font-awesome-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="logs" data-cy="entityLogButton">日志</el-dropdown-item>
                        <el-dropdown-item command="state" data-cy="entityStateButton">状态</el-dropdown-item>
                        <el-dropdown-item command="move" data-cy="entityMoveDirectoryButton">移动到目录</el-dropdown-item>
                        <el-dropdown-item command="export" data-cy="entityExportButton">导出</el-dropdown-item>
                        <el-dropdown-item command="delete" divided data-cy="entityDeleteButton">删除</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-show="eTLTasks && eTLTasks.length > 0">
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
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="eTLTaskDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除 ETL 任务 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeETLTask()">删除</el-button>
        </div>
      </template>
    </app-modal>
    <app-modal ref="onlineModal" id="onlineModal" size="md">
      <template #modal-title>
        <span>上线任务</span>
      </template>
      <div class="modal-body online-modal-body">
        <p>
          任务：<strong>{{ onlineTask?.taskName }}</strong>
        </p>
        <el-alert
          v-if="!onlineTask?.cron"
          type="warning"
          :closable="false"
          show-icon
          title="当前任务尚未设置调度周期，请设置 Cron 表达式后再上线。"
          class="mb-3"
        />
        <p v-else class="online-current-cron">
          当前调度周期：<code>{{ onlineTask.cron }}</code>
        </p>
        <div class="online-cron-field">
          <label class="online-cron-label">调度周期（Cron 表达式）</label>
          <CronExpressionSelector v-model:value="onlineCron" />
        </div>
        <div class="online-policy-row">
          <div class="online-policy-field">
            <label class="online-cron-label">重试次数</label>
            <el-input-number v-model="onlineRetryCount" :min="0" :max="10" :step="1" controls-position="right" />
            <span class="online-policy-tip">0 表示不重试，任务失败或超时后自动重试</span>
          </div>
          <div class="online-policy-field">
            <label class="online-cron-label">超时时间（秒）</label>
            <el-input-number v-model="onlineTimeoutSeconds" :min="0" :max="604800" :step="60" controls-position="right" />
            <span class="online-policy-tip">0 表示不限制，每次尝试单独计时</span>
          </div>
          <div class="online-policy-field">
            <label class="online-cron-label">优先级</label>
            <el-radio-group v-model="onlinePriority">
              <el-radio-button :value="3">高</el-radio-button>
              <el-radio-button :value="2">中</el-radio-button>
              <el-radio-button :value="1">低</el-radio-button>
            </el-radio-group>
            <span class="online-policy-tip">仅影响排队顺序，不抢占正在执行的任务</span>
          </div>
        </div>
        <p class="text-muted online-cron-tip">任务上线后将按该 Cron 表达式自动调度，如需调整可在此修改。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeOnlineModal" :disabled="isOnlineSubmitting">取消</el-button>
          <el-button
            type="primary"
            :loading="isOnlineSubmitting"
            :disabled="!onlineCronValid || !onlinePolicyValid"
            @click="confirmOnline"
            data-cy="entityConfirmOnlineButton"
          >
            确认上线
          </el-button>
        </div>
      </template>
    </app-modal>
    <app-modal ref="instancesModal" id="instancesModal" size="xl" scrollable>
      <template #modal-title>
        <span>任务执行实例 - {{ currentTask?.taskName }}</span>
      </template>
      <div class="modal-body">
        <div v-if="isInstancesLoading" class="text-center">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载实例列表...</span>
        </div>
        <div v-else-if="taskInstances.length === 0" class="text-center text-muted">暂无执行实例</div>
        <el-table v-else :data="taskInstances" style="width: 100%" size="small" max-height="400">
          <el-table-column prop="jobName" label="实例名称" min-width="200" show-overflow-tooltip></el-table-column>
          <el-table-column prop="parentJobName" label="父任务" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              {{ scope.row.parentJobName || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <span :class="'status-badge status-' + (scope.row.status?.toLowerCase() || '')">
                {{ scope.row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" width="160">
            <template #default="scope">
              {{ formatDateTime(scope.row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="endTime" label="结束时间" width="160">
            <template #default="scope">
              {{ formatDateTime(scope.row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="prepareViewLog(scope.row)"
                :disabled="scope.row.status === 'RUNNING' || scope.row.status === 'STARTING'"
              >
                日志
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeInstancesModal()">关闭</el-button>
        </div>
      </template>
    </app-modal>
    <app-modal ref="logModal" id="logModal" size="xl" scrollable>
      <template #modal-title>
        <span>{{ isRealtimeLog ? '实时任务日志' : '任务日志' }} - {{ currentLogInstance?.instanceCode }}</span>
      </template>
      <div class="modal-body">
        <div v-if="isRealtimeLog" class="log-hint">
          最近一次执行实例日志，最多显示最近 {{ logMaxLines }} 行；自动刷新间隔 3 秒，滚动到底部时跟随最新日志。
        </div>
        <div v-if="isLogLoading && !logContent" class="text-center">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载日志...</span>
        </div>
        <pre
          v-show="!isLogLoading || logContent"
          ref="logContentRef"
          class="log-content"
          style="
            max-height: 400px;
            overflow-y: auto;
            background-color: #f8f9fa;
            padding: 10px;
            border-radius: 4px;
            font-size: 12px;
            white-space: pre-wrap;
            word-break: break-all;
          "
          @scroll="handleLogScroll"
          >{{ logContent || '暂无日志内容' }}</pre
        >
      </div>
      <template #modal-footer>
        <div class="log-footer">
          <el-switch v-if="isRealtimeLog" v-model="logAutoRefresh" active-text="自动刷新" @change="handleLogAutoRefreshChange" />
          <el-button type="primary" @click="handleLogRefresh" :disabled="isLogLoading">刷新</el-button>
          <el-button @click="closeLogModal()">关闭</el-button>
        </div>
      </template>
    </app-modal>
    <app-modal ref="stateModal" id="stateModal" size="xl" scrollable>
      <template #modal-title>
        <span>任务状态管理 - {{ currentStateTask?.taskName }}</span>
      </template>
      <div class="modal-body">
        <div v-if="isStateLoading" class="text-center py-3">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载状态...</span>
        </div>
        <template v-else>
          <div v-if="stateGroups.length === 0" class="text-muted mb-2">当前任务暂无状态数据</div>
          <div
            v-for="group in stateGroups"
            :key="group.nodeId"
            class="mb-3"
            style="border: 1px solid #ebeef5; border-radius: 4px; overflow: hidden"
          >
            <div style="padding: 8px 12px; background-color: #f5f7fa; font-weight: 600; word-break: break-all">
              节点：{{ nodeDisplayName(group.nodeId) }}
              <span v-if="nodeDisplayName(group.nodeId) !== group.nodeId" class="text-muted" style="font-weight: normal; font-size: 12px">
                ({{ group.nodeId }})
              </span>
            </div>
            <el-table :data="group.rows" size="small" style="width: 100%">
              <el-table-column label="状态项" min-width="240">
                <template #default="scope">
                  <span style="word-break: break-all">{{ shortKey(scope.row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="值" min-width="240">
                <template #default="scope">
                  <el-input v-model="scope.row.value" size="small" placeholder="状态值"></el-input>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="scope">
                  <el-button type="danger" size="small" plain @click="removeStateRow(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
      <template #modal-footer>
        <div>
          <el-button type="danger" plain @click="clearState" :disabled="isStateSaving">清空状态</el-button>
          <el-button type="primary" @click="saveState" :disabled="isStateSaving">保存修改</el-button>
          <el-button @click="closeStateModal()">关闭</el-button>
        </div>
      </template>
    </app-modal>
    <app-modal v-model="importResultVisible" id="importResultModal" size="lg" scrollable>
      <template #modal-title>
        <span>ETL 任务导入结果</span>
      </template>
      <div class="modal-body" v-if="importResult">
        <p>
          任务名称：<strong>{{ importResult.taskName }}</strong>
        </p>
        <p>
          任务编码：<strong>{{ importResult.taskCode }}</strong>
          <span v-if="importResult.renamed" class="text-warning">（因冲突已自动重命名）</span>
        </p>
        <div v-if="importUnmatchedRefs(importResult).length">
          <p class="text-danger">以下引用的数据源 / 模型未在当前租户找到，已保留原值，需要手动修复：</p>
          <ul>
            <li v-for="(name, idx) in importUnmatchedRefs(importResult)" :key="idx">{{ name }}</li>
          </ul>
        </div>
        <div v-if="importResult.warnings && importResult.warnings.length">
          <p>提示：</p>
          <ul>
            <li v-for="(w, idx) in importResult.warnings" :key="idx">{{ w }}</li>
          </ul>
        </div>
        <p class="text-muted" style="margin-top: 12px">任务已离线导入，可在列表中编辑或上线。</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button type="primary" @click="closeImportResult">关闭</el-button>
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
          任务：<strong>{{ moveTask?.taskName }}</strong>
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
              placeholder="不选择表示未分类"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <p class="text-muted mb-0">清空选择后，任务将移动到「未分类」。</p>
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

<script lang="ts" src="./etl-task.component.ts"></script>

<style scoped>
.etl-task-container {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
}

.etl-task-layout {
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
  padding: 1px 8px;
  border-radius: 2px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
  background: #f2f3f5;
  color: #4e5969;
}

.status-online,
.status-successful {
  background: #e6f9ec;
  color: #00b42a;
}

.status-running,
.status-appending,
.status-starting {
  background: #e8f0fe;
  color: #2f6fed;
}

.status-waiting,
.status-interrupted {
  background: #fff1e3;
  color: #ff7d00;
}

.status-failed,
.status-timeout {
  background: #fdecec;
  color: #f53f3f;
}

.status-offline {
  background: #f2f3f5;
  color: #4e5969;
}

.online-modal-body p {
  margin-bottom: 12px;
}

.online-current-cron code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f5f7fa;
  color: #409eff;
}

.online-cron-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.online-cron-label {
  font-size: 13px;
  color: #606266;
}

.online-policy-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 16px;
}

.online-policy-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 200px;
}

.online-policy-tip {
  font-size: 12px;
  color: #909399;
}

.online-cron-tip {
  margin-top: 12px;
  margin-bottom: 0;
}

.log-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: #909399;
}

.log-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
</style>
