<template>
  <div>
    <h2 id="page-heading" data-cy="ETLTaskHeading" class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap">
      <span id="etl-task-heading">ETL 任务列表</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 280px"
          v-model="search"
          placeholder="按任务名称 / 描述搜索"
        />
        <button class="btn btn-info mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>刷新</span>
        </button>
        <router-link :to="{ name: 'ETLTaskDesignNew' }" custom v-slot="{ navigate }">
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-etl-task"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建 ETL 任务</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && eTLTasks && eTLTasks.length === 0">
      <span>未找到 ETL 任务</span>
    </div>
    <div v-if="eTLTasks && eTLTasks.length > 0">
      <el-table :data="eTLTasks" style="width: 100%" @sort-change="handleSortChange">
        <el-table-column prop="taskName" label="任务名称" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="jobId" label="作业ID" sortable="custom" width="100"></el-table-column>
        <el-table-column prop="taskDesc" label="任务描述" sortable="custom" width="150"></el-table-column>
        <el-table-column prop="cron" label="Cron 表达式" sortable="custom" width="130"></el-table-column>
        <el-table-column prop="status" label="状态" sortable="custom" width="100">
          <template #default="scope">
            <span :class="'status-badge status-' + (scope.row.status?.toLowerCase() || '')">
              {{ scope.row.status }}
            </span>
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
        <el-table-column label="操作" fixed="right" min-width="400">
          <template #default="scope">
            <div class="btn-group">
              <router-link :to="{ name: 'ETLTaskDesign', params: { eTLTaskId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button @click="navigate" class="btn btn-primary btn-sm edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button
                @click="runETLTask(scope.row.id)"
                class="btn btn-success btn-sm run"
                data-cy="entityRunButton"
              >
                <span class="d-none d-md-inline">立即执行</span>
              </el-button>
              <el-button
                v-if="scope.row.status === 'OFFLINE'"
                @click="onlineETLTask(scope.row.id)"
                class="btn btn-warning btn-sm online"
                data-cy="entityOnlineButton"
              >
                <span class="d-none d-md-inline">上线</span>
              </el-button>
              <el-button
                v-if="scope.row.status != 'OFFLINE'"
                @click="offlineETLTask(scope.row.id)"
                class="btn btn-secondary btn-sm offline"
                data-cy="entityOfflineButton"
              >
                <span class="d-none d-md-inline">下线</span>
              </el-button>
              <el-button @click="prepareViewInstances(scope.row)" class="btn btn-info btn-sm" data-cy="entityLogButton">
                <span class="d-none d-md-inline">日志</span>
              </el-button>
              <el-button
                @click="prepareRemove(scope.row)"
                variant="danger"
                class="btn btn-danger btn-sm"
                data-cy="entityDeleteButton"
                v-b-modal.removeEntity
              >
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <b-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="eTLTaskDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除 ETL 任务 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button type="button" class="btn btn-secondary" @click="closeDialog()">取消</button>
          <button type="button" class="btn btn-primary" data-cy="entityConfirmDeleteButton" @click="removeETLTask()">删除</button>
        </div>
      </template>
    </b-modal>
    <b-modal ref="instancesModal" id="instancesModal" size="xl" scrollable>
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
          <el-table-column prop="id" label="ID" width="70"></el-table-column>
          <el-table-column prop="instanceCode" label="实例编码" width="180"></el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <span :class="'status-badge status-' + (scope.row.status?.toLowerCase() || '')">
                {{ scope.row.status }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="jobMessage" label="消息" show-overflow-tooltip></el-table-column>
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
          <button type="button" class="btn btn-secondary" @click="closeInstancesModal()">关闭</button>
        </div>
      </template>
    </b-modal>
    <b-modal ref="logModal" id="logModal" size="xl" scrollable>
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
          <button class="btn btn-primary mr-2" @click="refreshLog" :disabled="isLogLoading">刷新</button>
          <button type="button" class="btn btn-secondary" @click="closeLogModal()">关闭</button>
        </div>
      </template>
    </b-modal>
    <div v-show="eTLTasks && eTLTasks.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count :page="page" :total="queryCount" :itemsPerPage="itemsPerPage"></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination size="md" :total-rows="totalItems" v-model="page" :per-page="itemsPerPage"></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./etl-task.component.ts"></script>
