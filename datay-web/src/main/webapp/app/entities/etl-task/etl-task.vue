<template>
  <div>
    <h2 id="page-heading" data-cy="ETLTaskHeading" class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap">
      <span id="etl-task-heading">数据集成</span>
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
        <router-link :to="{ name: 'ETLTaskDesignNew' }" custom v-slot="{ navigate }">
          <el-button
            type="primary"
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="jh-create-entity create-etl-task"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建 ETL 任务</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <div class="alert alert-warning" v-if="!isFetching && eTLTasks && eTLTasks.length === 0">
      <span>未找到 ETL 任务</span>
    </div>
    <div v-if="eTLTasks && eTLTasks.length > 0">
      <el-table
        :data="eTLTasks"
        style="width: 100%"
        :default-sort="{ prop: 'updateTime', order: 'descending' }"
        @sort-change="handleSortChange"
      >
        <el-table-column prop="taskName" label="任务名称" sortable="custom" width="300"></el-table-column>
        <el-table-column prop="taskDesc" label="任务描述" sortable="custom" width="300" show-overflow-tooltip></el-table-column>
        <el-table-column prop="cron" label="Cron 表达式" sortable="custom" width="130"></el-table-column>
        <el-table-column prop="status" label="状态" sortable="custom" width="100">
          <template #default="scope">
            <span :class="'status-badge status-' + (scope.row.status?.toLowerCase() || '')">
              {{ scope.row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" sortable="custom" width="90">
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
        <el-table-column label="操作" fixed="right" min-width="360">
          <template #default="scope">
            <div class="btn-group">
              <router-link :to="{ name: 'ETLTaskDesign', params: { eTLTaskId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button type="success" size="small" @click="runETLTask(scope.row.id)" class="run" data-cy="entityRunButton">
                <span class="d-none d-md-inline">立即执行</span>
              </el-button>
              <el-button
                type="warning"
                size="small"
                v-if="scope.row.status === 'OFFLINE'"
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
        <span>任务日志 - {{ currentLogInstance?.instanceCode }}</span>
      </template>
      <div class="modal-body">
        <div v-if="isLogLoading" class="text-center">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载日志...</span>
        </div>
        <pre
          v-else
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
          >{{ logContent || '暂无日志内容' }}</pre
        >
      </div>
      <template #modal-footer>
        <div>
          <el-button type="primary" @click="refreshLog" :disabled="isLogLoading">刷新</el-button>
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
</template>

<script lang="ts" src="./etl-task.component.ts"></script>

<style scoped>
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
</style>
