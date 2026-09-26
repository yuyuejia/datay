<template>
  <div>
    <h2 id="page-heading" data-cy="JobInstanceHeading" class="d-flex align-items-center justify-content-between flex-wrap-nowrap">
      <span id="job-instance-heading">任务实例列表</span>
      <div class="d-flex align-items-center">
        <el-button type="info" class="mr-2" @click="handleSyncList" :disabled="isFetching">
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon> <span>刷新</span>
        </el-button>
        <router-link :to="{ name: 'JobInstanceCreate' }" custom v-slot="{ navigate }">
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-job-instance">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>创建任务实例</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div class="alert alert-warning" v-if="!isFetching && jobInstances && jobInstances.length === 0">
      <span>未找到任务实例</span>
    </div>
    <div v-if="jobInstances && jobInstances.length > 0">
      <!-- 使用 el-table 组件，添加高度支持滚动条，绑定排序事件 -->
      <el-table :data="jobInstances" style="width: 100%" @sort-change="handleSortChange">
        <!-- <el-table-column prop="instanceCode" label="Instance Code" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="jobName" label="任务名称" sortable="custom" width="150"></el-table-column>
        <!-- <el-table-column prop="jobCode" label="Job Code" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="type" label="类型" sortable="custom" width="100"></el-table-column>
        <!-- <el-table-column prop="jobContext" label="Job Context" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="status" label="状态" sortable="custom" width="150">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <!-- <el-table-column prop="jobMessage" label="Job Message" sortable="custom" width="150"></el-table-column> -->
        <el-table-column prop="startTime" label="开始时间" sortable="custom" width="200">
          <template #default="scope">
            {{ formatDateTime(scope.row.startTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" sortable="custom" width="200">
          <template #default="scope">
            {{ formatDateTime(scope.row.endTime) || '' }}
          </template>
        </el-table-column>
        <el-table-column prop="execNode" label="执行节点" sortable="custom" width="180"></el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" width="200">
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || '' }}
          </template>
        </el-table-column>
        <!-- <el-table-column prop="project" label="Project" sortable="custom" width="150"></el-table-column> -->
        <!-- <el-table-column prop="tenantId" label="Tenant Id" sortable="custom" width="150"></el-table-column> -->
        <el-table-column label="" fixed="right" min-width="250">
          <template #default="scope">
            <div class="btn-group">
              <router-link :to="{ name: 'JobInstanceView', params: { jobInstanceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button type="info" size="small" @click="navigate" class="details" data-cy="entityDetailsButton">
                  <span class="d-none d-md-inline">查看</span>
                </el-button>
              </router-link>
              <router-link :to="{ name: 'JobInstanceEdit', params: { jobInstanceId: scope.row.id } }" custom v-slot="{ navigate }">
                <el-button type="primary" size="small" @click="navigate" class="edit" data-cy="entityEditButton">
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <!-- 新增：查看日志按钮 -->
              <el-button type="warning" size="small" @click="prepareViewLog(scope.row)" class="log" data-cy="entityLogButton">
                <span class="d-none d-md-inline">日志</span>
              </el-button>
              <!-- 新增：终止任务按钮 -->
              <el-button type="danger" size="small" v-if="scope.row.status === 'RUNNING' || scope.row.status === 'STARTING'" @click="prepareStopJobInstance(scope.row)" class="stop" data-cy="entityStopButton">
                <span class="d-none d-md-inline">终止</span>
              </el-button>
              <el-button size="small" @click="prepareRemove(scope.row)" type="danger" data-cy="entityDeleteButton">
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span id="datafusionApp.jobInstance.delete.question" data-cy="jobInstanceDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p id="jhi-delete-jobInstance-heading">你确定要删除任务实例 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">取消</el-button>
          <el-button type="primary" id="jhi-confirm-delete-jobInstance" data-cy="entityConfirmDeleteButton" @click="removeJobInstance()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>

    <!-- 新增：终止任务确认模态框 -->
    <app-modal ref="stopEntity" id="stopEntity">
      <template #modal-title>
        <span id="datafusionApp.jobInstance.stop.question" data-cy="jobInstanceStopDialogHeading">确认终止任务</span>
      </template>
      <div class="modal-body">
        <p id="jhi-stop-jobInstance-heading">
          你确定要终止任务实例
          <strong>{{ currentStopInstance?.jobName }}</strong>
          ({{ currentStopInstance?.instanceCode }}) 吗？
        </p>
        <p class="text-warning">
          <font-awesome-icon icon="exclamation-triangle"></font-awesome-icon>
          注意：此操作将强制终止正在运行的任务，可能会导致数据不一致。
        </p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeStopDialog()">取消</el-button>
          <el-button type="danger" id="jhi-confirm-stop-jobInstance" data-cy="entityConfirmStopButton" @click="stopJobInstance()">
            确认终止
          </el-button>
        </div>
      </template>
    </app-modal>

    <!-- 新增：查看日志模态框 -->
    <app-modal ref="logEntity" id="logEntity" size="xl" scrollable>
      <template #modal-title>
        <span id="datafusionApp.jobInstance.log.title" data-cy="jobInstanceLogDialogHeading">
          任务执行日志 - {{ currentLogInstance?.jobName }} ({{ currentLogInstance?.instanceCode }})
        </span>
      </template>
      <div class="modal-body">
        <div v-if="isLogLoading" class="text-center">
          <font-awesome-icon icon="spinner" spin></font-awesome-icon>
          <span>正在加载日志...</span>
        </div>
        <div v-else>
          <pre
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
          <el-button @click="closeLogDialog()">关闭</el-button>
        </div>
      </template>
    </app-modal>

    <div v-show="jobInstances && jobInstances.length > 0">
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

<script lang="ts" src="./job-instance.component.ts"></script>