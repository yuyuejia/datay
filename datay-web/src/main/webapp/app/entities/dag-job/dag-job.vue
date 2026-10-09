<template>
  <div>
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
        <router-link
          :to="{ name: 'DagJobDesignNew' }"
          custom
          v-slot="{ navigate }"
        >
          <el-button type="primary" @click="navigate" id="jh-create-entity" data-cy="entityCreateButton" class="jh-create-entity create-dag-job">
            <font-awesome-icon icon="plus"></font-awesome-icon>
            <span>新建编排</span>
          </el-button>
        </router-link>
      </div>
    </h2>
    <br />
    <div
      class="alert alert-warning"
      v-if="!isFetching && dagJobs && dagJobs.length === 0"
    >
      <span>尚未创建任务编排，点击「新建编排」开始设计 DAG。</span>
    </div>
    <div v-if="dagJobs && dagJobs.length > 0">
      <el-table
        :data="dagJobs"
        style="width: 100%"
        @sort-change="handleSortChange"
      >
        <el-table-column
          prop="jobName"
          label="编排名称"
          sortable="custom"
          min-width="160"
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
                :to="{ name: 'DagJobDesign', params: { jobId: scope.row.id } }"
                custom
                v-slot="{ navigate }"
              >
                <el-button
                  type="primary"
                  size="small"
                  @click="navigate"
                  data-cy="entityDesignButton"
                >
                  <span class="d-none d-md-inline">设计</span>
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
              >
                <span class="d-none d-md-inline">删除</span>
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <app-modal ref="removeEntity" id="removeEntity">
      <template #modal-title>
        <span data-cy="dagJobDeleteDialogHeading">确认删除</span>
      </template>
      <div class="modal-body">
        <p>确定要删除任务编排 {{ removeId }} 吗？</p>
      </div>
      <template #modal-footer>
        <div>
          <el-button @click="closeDialog()">
            取消
          </el-button>
          <el-button type="primary" data-cy="entityConfirmDeleteButton" @click="removeDagJob()">
            删除
          </el-button>
        </div>
      </template>
    </app-modal>
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
</template>

<script lang="ts" src="./dag-job.component.ts"></script>

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
