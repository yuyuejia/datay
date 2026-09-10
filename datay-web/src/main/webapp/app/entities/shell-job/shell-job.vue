<template>
  <div>
    <h2
      id="page-heading"
      data-cy="ShellJobHeading"
      class="d-flex align-items-center justify-content-between flex-nowrap flex-wrap-nowrap"
    >
      <span id="shell-job-heading">Shell 任务</span>
      <div class="d-flex align-items-center">
        <input
          type="text"
          class="form-control mr-2"
          style="width: 280px"
          v-model="search"
          placeholder="按名称搜索"
        />
        <button
          class="btn btn-info mr-2"
          @click="handleSyncList"
          :disabled="isFetching"
        >
          <font-awesome-icon icon="sync" :spin="isFetching"></font-awesome-icon>
          <span>刷新</span>
        </button>
        <router-link
          :to="{ name: 'ShellJobCreate' }"
          custom
          v-slot="{ navigate }"
        >
          <button
            @click="navigate"
            id="jh-create-entity"
            data-cy="entityCreateButton"
            class="btn btn-primary jh-create-entity create-shell-job"
          >
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建 Shell 任务</span>
          </button>
        </router-link>
      </div>
    </h2>
    <br />
    <div
      class="alert alert-warning"
      v-if="!isFetching && shellJobs && shellJobs.length === 0"
    >
      <span>尚未创建 Shell 任务，点击「新建 Shell 任务」开始创建。</span>
    </div>
    <div v-if="shellJobs && shellJobs.length > 0">
      <el-table
        :data="shellJobs"
        style="width: 100%"
        @sort-change="handleSortChange"
      >
        <el-table-column
          prop="jobName"
          label="任务名称"
          sortable="custom"
          min-width="160"
        ></el-table-column>
        <el-table-column
          prop="id"
          label="ID"
          sortable="custom"
          width="90"
        ></el-table-column>
        <el-table-column
          prop="cron"
          label="Cron 表达式"
          sortable="custom"
          width="150"
        >
          <template #default="scope">
            <span>{{ scope.row.cron || "未设置" }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          sortable="custom"
          width="110"
        >
          <template #default="scope">
            <span
              :class="
                'status-badge status-' + (scope.row.status?.toLowerCase() || '')
              "
            >
              {{ scope.row.status || "OFFLINE" }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="updateTime"
          label="更新时间"
          sortable="custom"
          width="160"
        >
          <template #default="scope">
            {{ formatDateShort(scope.row.updateTime) || "" }}
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="创建时间"
          sortable="custom"
          width="160"
        >
          <template #default="scope">
            {{ formatDateShort(scope.row.createTime) || "" }}
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" min-width="340">
          <template #default="scope">
            <div class="btn-group">
              <router-link
                :to="{ name: 'ShellJobEdit', params: { jobId: scope.row.id } }"
                custom
                v-slot="{ navigate }"
              >
                <el-button
                  type="primary"
                  size="small"
                  @click="navigate"
                  data-cy="entityEditButton"
                >
                  <span class="d-none d-md-inline">编辑</span>
                </el-button>
              </router-link>
              <el-button
                type="success"
                size="small"
                @click="handleExecuteOnce(scope.row)"
                data-cy="entityRunButton"
              >
                <span class="d-none d-md-inline">立即执行</span>
              </el-button>
              <el-button
                v-if="scope.row.status === 'OFFLINE'"
                type="warning"
                size="small"
                @click="handleOnline(scope.row)"
              >
                <span class="d-none d-md-inline">上线</span>
              </el-button>
              <el-button
                v-if="scope.row.status !== 'OFFLINE'"
                type="info"
                size="small"
                @click="handleOffline(scope.row)"
              >
                <span class="d-none d-md-inline">下线</span>
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="prepareRemove(scope.row)"
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
        <span data-cy="shellJobDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除 Shell 任务 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            @click="closeDialog()"
          >
            取消
          </button>
          <button
            type="button"
            class="btn btn-primary"
            data-cy="entityConfirmDeleteButton"
            @click="removeShellJob()"
          >
            删除
          </button>
        </div>
      </template>
    </b-modal>
    <div v-show="shellJobs && shellJobs.length > 0">
      <div class="row justify-content-center">
        <jhi-item-count
          :page="page"
          :total="queryCount"
          :items-per-page="itemsPerPage"
        ></jhi-item-count>
      </div>
      <div class="row justify-content-center">
        <b-pagination
          size="md"
          :total-rows="totalItems"
          v-model="page"
          :per-page="itemsPerPage"
        ></b-pagination>
      </div>
    </div>
  </div>
</template>

<script lang="ts" src="./shell-job.component.ts"></script>

<style scoped>
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
