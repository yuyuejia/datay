<template>
  <div class="row justify-content-center">
    <div class="col-12">
      <div v-if="eTLTask">
        <div class="d-flex justify-content-between align-items-center mb-3">
          <h2 class="jh-entity-heading mb-0" data-cy="eTLTaskDetailsHeading"><span>ETL Task</span> {{ eTLTask.taskName }}</h2>
          <div>
            <el-button type="info" size="small" native-type="submit" @click.prevent="previousState()" data-cy="entityDetailsBackButton">
              <font-awesome-icon icon="arrow-left"></font-awesome-icon>&nbsp;<span>返回</span>
            </el-button>
            <el-button type="info" size="small" @click="handleSyncList" :disabled="isFetchingInstances">
              <font-awesome-icon icon="sync" :spin="isFetchingInstances"></font-awesome-icon>&nbsp;<span>刷新</span>
            </el-button>
          </div>
        </div>
      </div>

      <!-- Task Instances Section -->
      <div v-if="eTLTask.id" class="mt-4">
        <div class="alert alert-warning" v-if="!isFetchingInstances && taskInstances && taskInstances.length === 0">
          <span>暂无任务实例</span>
        </div>

        <div v-if="taskInstances && taskInstances.length > 0">
          <el-table :data="taskInstances" style="width: 100%">
            <el-table-column prop="id" label="ID" width="80"></el-table-column>
            <el-table-column prop="instanceCode" label="实例编码" width="180"></el-table-column>
            <el-table-column prop="jobName" label="任务名称" width="150"></el-table-column>
            <el-table-column prop="type" label="类型" width="100"></el-table-column>
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <span :class="getStatusBadgeClass(scope.row.status)">{{ scope.row.status }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="170">
              <template #default="scope">
                {{ formatDateTime(scope.row.startTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="endTime" label="结束时间" width="170">
              <template #default="scope">
                {{ formatDateTime(scope.row.endTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="execNode" label="执行节点" width="130"></el-table-column>
            <el-table-column prop="jobMessage" label="消息" show-overflow-tooltip>
              <template #default="scope">
                <span v-if="scope.row.jobMessage">{{ scope.row.jobMessage }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="120">
              <template #default="scope">
                <el-button type="primary" size="small" @click="prepareViewLog(scope.row)">
                  <font-awesome-icon icon="file-alt"></font-awesome-icon>&nbsp;<span>日志</span>
                </el-button>
              </template>
            </el-table-column>
          </el-table>

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

    <!-- Log Modal -->
    <app-modal ref="logEntity" id="logEntity" size="xl" scrollable>
      <template #modal-title>
        <span> 任务执行日志 - {{ currentLogInstance?.jobName }} ({{ currentLogInstance?.instanceCode }}) </span>
      </template>
      <div class="modal-body">
        <div v-if="isLogLoading" class="text-center py-4">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载日志...</span>
        </div>
        <div v-else>
          <pre
            class="log-content"
            style="
              max-height: 500px;
              overflow-y: auto;
              background-color: #1e1e1e;
              color: #d4d4d4;
              padding: 15px;
              border-radius: 4px;
              font-size: 13px;
              white-space: pre-wrap;
              word-break: break-all;
              font-family: 'Courier New', monospace;
            "
            >{{ logContent || '暂无日志内容' }}</pre
          >
          <div v-if="hasMoreLog" class="text-center mt-2">
            <el-button type="primary" plain size="small" @click="loadMoreLog" :disabled="isLogLoading">
              <font-awesome-icon icon="arrow-down" :spin="isLogLoading"></font-awesome-icon>
              加载更多日志
            </el-button>
          </div>
        </div>
      </div>
      <template #modal-footer>
        <div>
          <el-button type="primary" @click="loadMoreLog" title="刷新">刷新</el-button>
          <el-button type="primary" @click="downloadLog" title="下载日志">下载日志</el-button>
          <el-button @click="closeLogDialog">关闭</el-button>
        </div>
      </template>
    </app-modal>
  </div>
</template>

<script lang="ts" src="./etl-task-details.component.ts"></script>
